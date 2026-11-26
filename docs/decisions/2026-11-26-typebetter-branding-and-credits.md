# TypeBetter branding and credits (2026-11-26)

David, 2026-11-26: "look at everything and make sure it's updated. I still want a spot to give credit to
them for the work that they did and the fact that we built off of it." (About in v108 still showed
Dictate's signature, website, "More apps", and upstream links.)

| ID | Decision | Why |
|---|---|---|
| BR-1 | About header line is "Built on Dictate and FlorisBoard" instead of upstream's personal signature. | The signature was DevEmperor's, not TypeBetter's; the new line credits both at first sight. |
| BR-2 | About gets a **Credits** section: "Built on Dictate" (opens github.com/DevEmperor/Dictate), "Based on FlorisBoard" (opens florisboard/florisboard), "Support Dictate's developer" (opens DevEmperor's donation page). | David wants a clear spot that thanks them. The donate link keeps a way to support the upstream developer without nagging. |
| BR-3 | Removed "More apps" (SayOVA, TunerToGo), its route, banners, strings and the one-time SayOVA Smartbar nudge. | Those are the upstream developer's other products, not TypeBetter's. |
| BR-4 | Removed the Website row (dictatekeyboard.com). Repository, Changelog, Privacy policy, issue tracker, commit links and the key-code reference now point at davidbalasa-ui/Typing-should-not-be-hard. Privacy policy opens docs/play/privacy-policy.md (TypeBetter's own). | TypeBetter has no website; its home is the repo. |
| BR-5 | Feedback opens the repo's GitHub issues instead of emailing contact@devemperor.net. | Mail to the upstream developer about TypeBetter would reach the wrong person; the TypeBetter privacy policy already names the issues page as the contact. |
| BR-6 | Dropped the Smartbar rate and donate nudges. | They opened Dictate's own Play listing and PayPal; TypeBetter is a private Play test with no public listing. Donation to Dictate's developer stays available under Credits. |
| BR-7 | Removed Wear settings' "Install on your watch" row. | It opened Dictate's Play listing, which installs a different app; TypeBetter publishes no watch app. |
| BR-8 | Every user-facing "Dictate" that names the app now says "TypeBetter", in English and all translations (and the Wear strings, debug/beta app names, clipboard labels, the Downloads/TypeBetter folder for saved failed recordings, and OpenRouter's app-attribution headers). Kept: "Dictate" as the verb, as the name of the voice settings screen ("Settings → Dictate"), "Dictate Cloud" (upstream's own paid service, see BR-10), and historical "Dictate 3". Translations whose meaning changed (feedback, tour outro, changelog donate line) were removed so those few lines show in English until retranslated. | One name everywhere; no wrong translations. |
| BR-9 | NOTICE gains a TypeBetter header and keeps Dictate's and FlorisBoard's notices verbatim. The app ships it (assets/license/notice.txt) and shows it above the Apache 2.0 text under About › Project license. A unit test keeps the two copies equal. | Apache 2.0 §4(d): a derivative must carry the upstream NOTICE in a readable form with the distribution. |
| BR-10 | Not changed here: Dictate Cloud (upstream's paid credit service on api.dictatekeyboard.com) is still offered in setup and the provider list, and the download URLs for models, dictionaries and the prompt library still use Dictate's GitHub releases. Help links still open Dictate's user guide. | Downloads and the guide work and describe the same features. Cloud cannot sell credit under TypeBetter's package id; hiding it is a separate product change, offered to David as a follow-up. |
