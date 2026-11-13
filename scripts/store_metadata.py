"""Create draft Amazon listing copy from public product text."""
import json
from pathlib import Path
from capture import LISTINGS
from localize import get_translation, LOCALES

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "deliverables/store-assets/metadata"
FIELDS = {
    "display_title": "Planavora – Event Planner & Budget Tracker",
    "short_description": "Plan your event, track its budget and manage the guest list from one offline event dashboard.",
    "long_description": "Planavora brings party checklists, guest lists, RSVP tracking and event expenses together in the Event Command Centre. See planned and actual spending, record deposits, track vendor arrangements and spot upcoming deadlines. Use the running order to see what happens on the day. Create an event on your device without an account, attach receipts and documents, and preview reports based on your saved records. Type a planning command or use the microphone if your device has a compatible recognition service.",
    "feature_1": "See your budget, guests and deadlines in the Event Command Centre.",
    "feature_2": "Track planned costs, actual spending and vendor balances.",
    "feature_3": "Manage guest lists and RSVP statuses by hand.",
    "feature_4": "Keep checklists, documents and event-day schedules on your device.",
    "feature_5": "Use typed commands and compatible device speech services.",
    "keywords": "event planner, party planner, event budget tracker, planning checklist, guest list, RSVP tracker, birthday organiser, wedding planner, vendor payments, seating plan, event countdown, offline event planner",
    "release_notes": "Initial release with event planning, budget tracking, guest management and a local event dashboard.",
    "support_copy": "For help, visit the Planavora support page. Please leave private event information out of public reports.",
    "screen_1": "All-in-one event planner",
    "screen_2": "Event budget and expense tracker",
    "screen_3": "Guest lists and RSVP tracking",
    "screen_4": "Offline planning assistant",
    "screen_5": "Voice and typed planning commands",
    "screen_6": "Vendor quotes and payments",
    "screen_7": "Event countdown and timeline",
    "screen_8": "Offline event reports",
}
CAPTIONS = {
    "caption_1": "Read your event at a glance.",
    "caption_2": "Compare planned and actual costs.",
    "caption_3": "See which guests have replied.",
    "caption_4": "Review a checklist tailored to your event type.",
    "caption_5": "Review a command before using it.",
    "caption_6": "Keep quotes and balances in one place.",
    "caption_7": "See what happens on the day.",
    "caption_8": "Preview reports from your saved records.",
}
URLS = {
    "support_url": "https://github.com/lanray07/Planavora/blob/main/SUPPORT.md",
    "privacy_url": "https://github.com/lanray07/Planavora/blob/main/PRIVACY.md",
    "developer_website": "https://github.com/lanray07/Planavora",
}

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    source = FIELDS | CAPTIONS
    cache = {}
    for listing, app_locale in LISTINGS.items():
        language = LOCALES[app_locale]
        if language not in cache:
            result = []
            lines = list(source.values())
            for start in range(0, len(lines), 8):
                batch = lines[start:start + 8]
                translated = batch if language == "en" else get_translation(language, batch)
                if len(translated) != len(batch):
                    translated = [get_translation(language, [line])[0] for line in batch]
                result.extend(translated)
            cache[language] = dict(zip(source, result, strict=True))
        record = cache[language].copy()
        record["display_title"] = "Planavora" + " – " + record["display_title"].partition("–")[2].strip() if "–" in record["display_title"] else record["display_title"]
        record["display_title"] = record["display_title"].replace("Planavora  ", "Planavora ")
        if len(record["display_title"]) > 200 or len(record["short_description"].encode()) > 2000 or len(record["long_description"]) > 4000:
            raise ValueError(f"Listing fields are too long for {listing}")
        record["feature_bullets"] = [record.pop(f"feature_{i}") for i in range(1, 6)]
        record["screenshot_copy"] = [{"headline": record.pop(f"screen_{i}"), "caption": record.pop(f"caption_{i}")} for i in range(1, 9)]
        record.update(URLS, listing_locale=listing, app_locale=app_locale, review="Draft pending human review")
        (OUT / f"{listing}.json").write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

if __name__ == "__main__":
    main()
