package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.core.diagnostics.DetectionEntry

/** Display fixed explanations only; never render unknown provider/user text. */
internal object IntroDebugText {
    fun status(code: String, he: Boolean): String {
        fun t(h: String, e: String) = if (he) h else e
        return when (code) {
            "waiting" -> t("ממתין להתחלת זיהוי; בדוק שהאיסוף מופעל", "Waiting for detection; check collection is enabled")
            "lookup" -> t("בודק אם כבר קיימים דיווחים ב־IntroDB", "Checking existing IntroDB reports")
            "subtitles" -> t("בודק כתוביות זמינות לניתוח", "Checking subtitles for analysis")
            "cloud" -> t("ממתין לתשובת ניתוח מהענן", "Waiting for cloud analysis")
            "media" -> t("מחלץ תמונות מהווידאו ברקע", "Extracting video frames in the background")
            "proposals" -> t("נמצאו הצעות לבדיקה בסיום הפרק", "Proposals ready for review at episode end")
            "already_reported" -> t("כל הדיווחים כבר קיימים; אין צורך לנתח", "Reports already exist; analysis skipped")
            "missing_metadata" -> t("חסרים מזהה תוכן או פרטי עונה ופרק", "Missing content or episode identification")
            "lookup_failed" -> t("בדיקת IntroDB נכשלה; הניתוח נדחה", "IntroDB check failed; analysis deferred")
            "cloud_disabled", "missing_key" -> t("ניתוח ענן כבוי או שחסר מפתח API", "Cloud analysis is off or an API key is missing")
            "no_proposals" -> t("הניתוח הסתיים ללא הצעות לדילוג", "Analysis finished without skip proposals")
            "no_evidence", "visual_unavailable", "media_unavailable" -> t("לא הושגו ראיות מספיקות לניתוח", "Could not obtain enough analysis evidence")
            "automatic_unavailable" -> t("שליחת AI אוטומטית עדיין אינה זמינה; בחר בדיקה בסיום", "Automatic AI submission is unavailable; use end review")
            "probe_running" -> t("בודק בקשת AI קטנה ללא תמונות", "Testing a small AI request without images")
            "probe_ok" -> t("בקשת AI קטנה התקבלה; ניתוח תמונות עדיין לא אומת", "Small AI request accepted; image analysis is not yet verified")
            "not_requested" -> t("בדיקת חיבור ידנית טרם בוצעה", "Manual connection test has not run")
            "network_timeout" -> t("התקשורת חרגה מזמן ההמתנה", "Network request timed out")
            "network_failed", "dns_failed", "tls_failed" -> t("החיבור לשרת נכשל", "Server connection failed")
            "response_invalid", "output_invalid" -> t("התקבלה תשובה שלא ניתן לנתח בבטחה", "Response could not be safely interpreted")
            "output_token_limit" -> t("תשובת ה־AI נקטעה במגבלת הפלט", "AI response hit its output limit")
            "output_blocked" -> t("השרת חסם את יצירת התשובה", "Provider blocked the response")
            "provider_key_invalid" -> t("Google דיווחה על מפתח לא תקין", "Google reported an invalid API key")
            "provider_key_restricted" -> t("Google דיווחה על הגבלה במפתח", "Google reported a key restriction")
            "provider_service_disabled" -> t("Google דיווחה שהשירות אינו מופעל בפרויקט", "Google reported the service is disabled")
            "provider_billing" -> t("Google דיווחה על הגדרת חיוב API חסרה", "Google reported API billing is disabled")
            "provider_permission" -> t("Google דיווחה על בעיית הרשאות", "Google reported a permission problem")
            "provider_quota" -> t("Google דיווחה על מגבלת משאבים או מכסה", "Google reported a resource or quota limit")
            "provider_unavailable" -> t("Google דיווחה שהשירות אינו זמין", "Google reported service unavailable")
            "provider_request_invalid" -> t("Google דיווחה על בקשה לא תקינה", "Google reported an invalid request")
            "provider_not_found" -> t("Google דיווחה שהמשאב לא נמצא", "Google reported resource not found")
            "provider_internal" -> t("Google דיווחה על תקלה פנימית", "Google reported an internal failure")
            "provider_unknown" -> t("לא התקבל פירוט שגיאה מוכר ובטוח להצגה", "No recognized safe provider error detail")
            else -> if (code.matches(Regex("http_[1-5][0-9]{2}"))) {
                val n = code.removePrefix("http_")
                if (n == "503") t("השרת החזיר 503: השירות אינו זמין", "Server returned 503: service unavailable")
                else t("השרת החזיר שגיאה $n", "Server returned error $n")
            } else t("הפעולה לא הושלמה; פירוט ביומן", "Action did not complete; see the journal")
        }
    }

    fun entry(row: DetectionEntry, he: Boolean): String {
        fun t(h: String, e: String) = if (he) h else e
        fun n(key: String) = row.metrics[key] ?: 0
        return when (row.event) {
            "embedded_scan" -> t("נמצאו ${n("embedded_count")} מסלולי טקסט נתמכים בתוך הקובץ", "Found ${n("embedded_count")} supported text tracks in the file")
            "embedded_subtitles" -> t("נקראו ${n("cue_count")} תיאורים מתוך ${n("embedded_count")} מסלולי כתוביות בקובץ", "Read ${n("cue_count")} observations from ${n("embedded_count")} embedded text tracks")
            "subtitle_search" -> t("בודק התאמה של ${n("candidate_count")} כתוביות מתוספים ומחיפוש נוסף", "Checking ${n("candidate_count")} subtitles from add-ons and extra search")
            "subtitle_choice" -> if (row.status == "subtitle_ready") {
                val source = when (n("source_code")) { 1L -> t("מובנית בקובץ", "embedded"); 2L -> t("בחירת המשתמש", "user-selected"); else -> t("התאמה אוטומטית", "automatically aligned") }
                t("נבחרה כתובית: $source, ${n("cue_count")} שורות, ${n("sdh_count")} תיאורים", "Subtitle chosen: $source, ${n("cue_count")} cues, ${n("sdh_count")} descriptions")
            } else t("לא נמצאה כתובית עם התאמת תזמון מספקת; נפסלו ${n("rejected_count")} מועמדים", "No suitable timed subtitle; ${n("rejected_count")} candidates rejected")
            "cloud_model", "probe_model" -> {
                val model = if (n("model_code") == 38L) "Gemini 3.8 Flash" else "Gemini 3.6 Flash"
                t("בקשת AI אל $model, ניסיון ${n("attempt")}", "AI request to $model, attempt ${n("attempt")}")
            }
            "cloud_fallback" -> t("השרת החזיר ${n("http_status")}; עובר ל־Gemini 3.6 Flash, ניסיון ${n("attempt")}", "Server returned ${n("http_status")}; trying Gemini 3.6 Flash, attempt ${n("attempt")}")
            "lookup_result" -> t("בדיקת המאגר הסתיימה: חסרים ${n("missing_count")} סוגי דיווח", "Database checked: ${n("missing_count")} report types missing")
            "subtitle_download" -> if (n("body_chars") == 0L) t("לא נטענו כתוביות לניתוח", "No subtitle text loaded") else t("טקסט הכתוביות נטען", "Subtitle text loaded")
            "subtitle_parsed" -> t("נקראו ${n("cue_count")} שורות כתוביות", "Parsed ${n("cue_count")} subtitle cues")
            "media_sample" -> t("חולצו ${n("frame_count")} תמונות מהווידאו", "Extracted ${n("frame_count")} video frames")
            "cloud_request" -> t("נשלחה בקשת ניתוח: ${n("frame_count")} תמונות, ${n("cue_count")} כתוביות", "Analysis requested: ${n("frame_count")} frames, ${n("cue_count")} cues")
            "cloud_retry" -> t("השרת החזיר ${n("http_status")}; ממתין לניסיון ${n("attempt")} מתוך 3", "Server returned ${n("http_status")}; waiting for attempt ${n("attempt")} of 3")
            "cloud_result" -> when (row.status) {
                "not_requested" -> t("לא נשלחה בקשת ניתוח כתוביות לענן", "No subtitle analysis request was sent")
                "completed", "visual_completed" -> t("הענן החזיר ${n("proposal_count")} הצעות", "Cloud returned ${n("proposal_count")} proposals")
                else -> status(row.status, he)
            }
            "review_offered" -> t("נשמרו ${n("proposal_count")} הצעות לבדיקה בסיום", "Saved ${n("proposal_count")} proposals for end review")
            else -> status(row.status, he)
        }
    }
}
