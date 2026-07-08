# Phase 5 — Fix Prompt (सीधे अपने कोडिंग AI/agent को दें)

> इस पूरे ब्लॉक को copy करके अपने repo पर काम कर रहे AI (Claude Code / Copilot /
> जो भी इस्तेमाल कर रहे हों) को एक ही बार में दे दीजिए। यह प्रॉम्प्ट diagnostic
> checklist से confirmed जानकारी पर आधारित है — इसमें कोई assumption नहीं है,
> इसलिए बिना पूछे सीधे apply किया जा सकता है, सिवाय जहाँ "VERIFY FIRST" लिखा है।

---

मेरे Android project (Kotlin, base package `com.yourname.githubmanager`) में
Phase 5 के दौरान कुछ फाइलों में गलत import और एक missing route की वजह से build
fail हो रहा है। नीचे दिए गए सभी बदलाव **exact और confirmed** हैं — बिना पूछे
apply करो। कहीं भी existing unrelated code मत छेड़ो।

## Context (ज़रूरी जानकारी)

- असली `FileNode` data class यहाँ है:
  `app/src/main/java/com/yourname/githubmanager/domain/Models.kt`
  (package: `com.yourname.githubmanager.domain`)
- यह package **`com.yourname.githubmanager.model` नहीं है** — कहीं भी
  `com.yourname.githubmanager.model.FileNode` import दिखे तो वह गलत है, यह
  package project में exist ही नहीं करता।

## Fix 1 — `data/filesystem/ProjectFileSystem.kt`

इस फाइल में यह गलत import लाइन है:
```kotlin
import com.yourname.githubmanager.model.FileNode
```
इसे बदलकर यह करो:
```kotlin
import com.yourname.githubmanager.domain.FileNode
```
(अगर लाइन के आगे कोई "Assumption:" comment है तो वह comment भी हटा दो, अब
यह assumption नहीं, confirmed fact है।)

## Fix 2 — `data/filesystem/LocalFileSystem.kt`

बिल्कुल वही गलती, वही fix:
```kotlin
import com.yourname.githubmanager.model.FileNode
```
बदलकर:
```kotlin
import com.yourname.githubmanager.domain.FileNode
```

## Fix 3 — `data/filesystem/SafFileSystem.kt`

इस फाइल में FileNode का import **सिरे से गायब है** (गलत नहीं, missing)। `package`
लाइन के ठीक बाद यह नई import लाइन जोड़ो:
```kotlin
import com.yourname.githubmanager.domain.FileNode
```

## Fix 4 — `navigation/Screen.kt`

`AppNavigator.kt` पहले से `Screen.Editor.route` और `Screen.Editor.createRoute(...)`
इस्तेमाल करता है, लेकिन `Screen.kt` में `Editor` destination कभी जोड़ा ही नहीं
गया — इसलिए अभी `Unresolved reference: Editor` वाला अगला compile error आएगा।
मौजूदा `Workspace`/`Settings` objects मत छेड़ो, सिर्फ यह नया destination जोड़ो
(sealed class के अंदर):

```kotlin
object Editor : Screen("editor/{filePath}") {
    fun createRoute(filePath: String): String {
        return "editor/${Uri.encode(filePath)}"
    }
}
```

`Uri.encode` के लिए फाइल के top पर `import android.net.Uri` भी जोड़ना होगा
अगर पहले से नहीं है।

## Fix 5 — VERIFY FIRST: Double-encoding check in `navigation/AppNavigator.kt`

`AppNavigator.kt` में एक जगह `Screen.Editor.createRoute(encodedPath)` कॉल हो
रहा है — वेरिएबल का नाम `encodedPath` है, जो इशारा करता है कि शायद path पहले
ही `Uri.encode()` से encode किया जा चुका है उस लाइन से पहले।

अगर ऐसा है (यानी `encodedPath` को बनाने वाली लाइन में पहले से `Uri.encode(...)`
कॉल हो रहा है), तो Fix 4 में जो `createRoute` बनाया गया है उसके अंदर से
`Uri.encode()` हटा दो — सिर्फ `"editor/$filePath"` रखो, ताकि path double-encode
न हो (double-encoding से editor screen में file resolve होना टूट जाएगा)।

अगर `encodedPath` variable actually raw/unencoded है (नाम भ्रामक है), तो Fix 4
जैसा ही रहने दो, कुछ मत बदलो।

**इस फाइल को पढ़कर खुद तय करो कि कौन सी स्थिति है, फिर वही ठीक करो — किसी एक
जगह encode हो, दोनों जगह नहीं।**

## Fix 6 — Extra/unnecessary फाइलें ढूँढो और हटाओ

Phase 5 के काम के दौरान अगर कोई ऐसी फाइल बन गई है जो:
- `data/filesystem/` या `ui/screens/editor/` के अंदर है,
- लेकिन project में कहीं भी import/reference नहीं होती (dead code है),
- और `ProjectFileSystem`, `SafFileSystem`, `LocalFileSystem`, `FileEditorScreen`,
  `FileEditorViewModel` — इनमें से कोई नहीं है,

तो उसे list करो और हटाने से पहले मुझे बताओ कि कौन सी फाइल और क्यों लगती है कि
वह ज़रूरत से ज़्यादा/duplicate बनी है। **`SafHelper.kt` और `ZipExtractor.kt` को
मत छेड़ना** — ये Phase 5 से पहले की मौजूदा फाइलें हैं, अनजान/extra नहीं हैं।

## आख़िर में

सारे बदलाव करने के बाद पूरा project compile करके confirm करो कि कोई नया
`Unresolved reference` error तो नहीं आया। अगर आया तो वह भी report करो, मुझसे
पूछे बिना खुद fix मत करना (सिर्फ Fix 1-6 apply करना है)।

