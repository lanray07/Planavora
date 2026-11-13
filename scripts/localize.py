"""Translate and validate public Android resources. Requires PLANAVORA_TRANSLATE_URL to generate."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import csv
import json
import os
from pathlib import Path
import re
import time
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
OUTPUT = ROOT / "deliverables/localization"
SOURCE = RES / "values/strings.xml"
LOCALES = {
    "en-GB": "en", "en-US": "en", "en-AU": "en", "es-ES": "es", "es-419": "es",
    "fr-FR": "fr", "fr-CA": "fr", "de-DE": "de", "it-IT": "it", "pt-BR": "pt",
    "pt-PT": "pt", "nl-NL": "nl", "pl-PL": "pl", "sv-SE": "sv", "ja-JP": "ja", "hi-IN": "hi",
    "ar": "ar", "tr-TR": "tr", "id-ID": "id", "ko": "ko",
    "zh-Hans": "zh-CN", "zh-Hant": "zh-TW",
}
PLACEHOLDER = re.compile(r"%\d+\$[sd]")
PLURALS = {"ar": ("zero", "one", "two", "few", "many", "other"), "pl": ("one", "few", "many", "other"),
           "fr": ("one", "many", "other"), "es": ("one", "many", "other"), "pt": ("one", "many", "other"),
           "it": ("one", "many", "other"), "ja": ("other",), "id": ("other",),
           "ko": ("other",), "zh-CN": ("other",), "zh-TW": ("other",)}
DAYS = {"ar": "الأيام المتبقية: %1$s", "ja": "あと%1$s日", "pl": "Do wydarzenia pozostało: %1$s dni",
        "ko": "행사까지 %1$s일", "zh-CN": "距活动还有 %1$s 天", "zh-TW": "距活動還有 %1$s 天"}
REGIONAL = {
    "en-US": {"command_centre": "Event Command Center", "uncategorised": "Uncategorized",
              "cancelled": "Canceled", "deposit_help": "Include any paid deposit in actual spending."},
    "ko": {"transcribing": "음성을 텍스트로 변환하는 중…", "command": "명령 또는 음성 변환 결과",
           "review_command": "명령 확인", "voice_rsvp/0": "응답을 기다리는 초대 보기",
           "voice_tasks/0": "남은 할 일 보기", "voice_payments/0": "미지급 업체 대금 보기",
           "voice_start/0": "행사는 몇 시에 시작하나요"},
    "zh-Hans": {"transcribing": "正在转写语音…", "listening": "正在聆听…",
                "invitation_line": "诚邀您参加%1$s！", "voice_rsvp/0": "查看尚未回复的邀请",
                "voice_payments/0": "查看尚未支付的供应商款项"},
    "zh-Hant": {"transcribing": "正在轉錄語音…", "invitation_line": "誠摯邀請您參加%1$s！",
                "voice_rsvp/0": "查看尚未回覆的邀請", "voice_payments/0": "查看尚未支付的廠商款項",
                "privacy_body": "活動和附件會儲存在這部裝置上。選用的語音辨識功能會使用您安裝的語音服務。報表只會在您選擇分享目的地後傳送。",
                "voice_privacy": "裝置的語音服務可能會在線上處理音訊。Planavora 不會儲存麥克風錄音。"},
}

def path(tag):
    return RES / ("values-b+" + tag.replace("-", "+")) / SOURCE.name

def entries(tree):
    for group in tree:
        name = group.attrib["name"]
        if group.tag == "string":
            yield name, group
        else:
            for index, item in enumerate(group):
                yield f"{name}/{item.get('quantity', index)}", item

def android(text):
    return text.replace(r"\'", "'").replace(r'\"', '"')

def escape(text):
    return text.replace("'", r"\'").replace('"', r'\"')

def get_translation(target, lines):
    url = os.environ["PLANAVORA_TRANSLATE_URL"]
    if not url.startswith("https://"):
        raise ValueError("Set a public text translation endpoint with HTTPS")
    protocol = os.environ.get("PLANAVORA_TRANSLATE_PROTOCOL", "gtx")
    if protocol == "gtx":
        query = urlencode({"client": "gtx", "sl": "en", "tl": target, "dt": "t", "q": "\n".join(lines)})
        request = Request(url + "?" + query, headers={"User-Agent": "Planavora localization"})
    else:
        headers = {"Content-Type": "application/json"}
        token = os.environ.get("PLANAVORA_TRANSLATE_TOKEN")
        if token:
            headers["Authorization"] = "Bearer " + token
        body = json.dumps({"source": "en", "target": target, "texts": lines}).encode()
        request = Request(url, body, headers)
    for attempt in range(3):
        try:
            with urlopen(request, timeout=20) as response:
                data = json.load(response)
            if protocol == "gtx":
                return "".join(segment[0] for segment in data[0] if segment[0]).splitlines()
            return data["translations"]
        except (OSError, ValueError):
            if attempt == 2:
                raise
            time.sleep(attempt + 1)

def translations(language, source):
    cache_file = OUTPUT / "cache" / f"{language}.json"
    cache_file.parent.mkdir(parents=True, exist_ok=True)
    cache = json.loads(cache_file.read_text(encoding="utf-8")) if cache_file.exists() else {}
    glossary = {"Planavora": "Planavora", "Planavora Premium": "Planavora Premium", "%1$s · %2$s": "%1$s · %2$s"}
    raw = [android(node.text) for _, node in entries(source)]
    lines = list(dict.fromkeys(value for value in raw if value not in cache and value not in glossary))
    for start in range(0, len(lines), 12):
        group = lines[start:start + 12]
        encoded = [PLACEHOLDER.sub(lambda m: "ZXV" + m[0][1] + m[0][-1].upper() + "VXZ", text) for text in group]
        result = get_translation(language, encoded)
        if len(result) != len(group):
            result = [get_translation(language, [line])[0] for line in encoded]
        for original, translated in zip(group, result, strict=True):
            restored = re.sub(r"ZXV\s*(\d)\s*([SD])\s*VXZ",
                              lambda m: "%" + m[1] + "$" + m[2].lower(), translated.strip(), flags=re.I)
            if sorted(PLACEHOLDER.findall(original)) != sorted(PLACEHOLDER.findall(restored)):
                raise ValueError(f"{language} lost a placeholder in {original!r}: {restored!r}")
            if not restored:
                raise ValueError(f"{language} returned an empty translation")
            cache[original] = restored
        cache_file.write_text(json.dumps(cache, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return cache | glossary

def generate(tags):
    targets = sorted({LOCALES[tag] for tag in tags} - {"en"})
    with ThreadPoolExecutor(max_workers=6) as pool:
        dictionaries = dict(zip(targets, pool.map(lambda lang: translations(lang, ET.parse(SOURCE).getroot()), targets)))
    for tag in tags:
        language = LOCALES[tag]
        tree = ET.parse(SOURCE).getroot()
        dictionary = dictionaries.get(language, {})
        plural = tree.find("plurals")
        default = {item.attrib["quantity"]: android(item.text) for item in plural}
        for item in list(plural):
            plural.remove(item)
        for quantity in PLURALS.get(language, ("one", "other")):
            ET.SubElement(plural, "item", {"quantity": quantity}).text = default.get(quantity, default["other"])
        for key, node in entries(tree):
            value = android(node.text)
            translated = dictionary.get(value, value)
            if key.startswith("days_left/"):
                translated = DAYS.get(language, translated)
            node.text = escape(REGIONAL.get(tag, {}).get(key, translated))
        output = path(tag)
        output.parent.mkdir(parents=True, exist_ok=True)
        ET.indent(tree, "    ")
        ET.ElementTree(tree).write(output, encoding="utf-8")
        with output.open("a", encoding="utf-8") as f:
            f.write("\n")

def validate():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    source = dict(entries(ET.parse(SOURCE).getroot()))
    broken = False
    with (OUTPUT / "coverage.csv").open("w", newline="", encoding="utf-8") as f:
        out = csv.writer(f)
        out.writerow(["locale", "interface_strings_and_voice_phrases", "expected", "placeholder_errors", "plural_quantities", "human_review"])
        for tag, language in LOCALES.items():
            expected = {key: value for key, value in source.items() if not key.startswith("days_left/")}
            for quantity in PLURALS.get(language, ("one", "other")):
                expected["days_left/" + quantity] = source["days_left/other"]
            actual = dict(entries(ET.parse(path(tag)).getroot())) if path(tag).exists() else {}
            missing = expected.keys() - actual.keys()
            mismatch = sum(sorted(PLACEHOLDER.findall(expected[k].text)) != sorted(PLACEHOLDER.findall(actual[k].text or ""))
                           for k in expected.keys() & actual.keys())
            out.writerow([tag, len(expected) - len(missing), len(expected), mismatch, len(PLURALS.get(language, ("one", "other"))), "pending"])
            broken = broken or bool(missing) or mismatch > 0
    if broken:
        raise SystemExit("Resource validation failed; see coverage.csv")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--translate", action="store_true")
    parser.add_argument("--locale", action="append", choices=LOCALES, help="Only regenerate these locales")
    args = parser.parse_args()
    if args.translate:
        generate(args.locale or LOCALES)
    validate()
