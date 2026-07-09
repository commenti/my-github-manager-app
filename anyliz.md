## Mission: Full project audit + confirmed root cause fix

Android project, Kotlin + Jetpack Compose.
Package: com.yourname.githubmanager
Repo already cloned / zip provided.

---

## Background

App do tarike se files import karta hai:
1. ZIP import → files extract hoti hain absolute path pe
   (e.g. /data/user/0/.../files/project/Main.kt)
2. SAF Folder import → content:// URIs milti hain
   (e.g. content://com.android.externalstorage.../document/...)

File tree mein file tap karne par editor screen
khulni chahiye — dono cases mein.

---

## Symptom

Dono cases mein yahi error aata hai:
"Failed to load file: Permission lost for file: <filename>"

Yeh error SafFileSystem.kt ke readText() se aata hai
(SecurityException catch block).

---

## What I need from you

### Step 1 — Read these files fully:
- navigation/AppNavigator.kt
- navigation/Screen.kt
- data/filesystem/SafFileSystem.kt
- data/filesystem/LocalFileSystem.kt
- data/filesystem/ProjectFileSystem.kt
- data/filesystem/FileSystemException.kt
- data/filesystem/SafHelper.kt
- ui/screens/workspace/MainWorkspaceScreen.kt
- ui/screens/workspace/MainWorkspaceViewModel.kt
- ui/components/FileTreeItem.kt
- ui/screens/editor/FileEditorScreen.kt
- ui/screens/editor/FileEditorViewModel.kt
- domain/Models.kt

### Step 2 — Trace exact code path for BOTH cases:

**Case A (ZIP import):**
- User zip select karta hai → kahan handle hota hai?
- FileNode.path mein kya value store hoti hai?
- File tap → AppNavigator → kaunsa FileSystem pick hota hai?
- readText() mein exactly kya fail hota hai?

**Case B (SAF Folder import):**
- User folder select karta hai → kahan handle hota hai?
- FileNode.path mein kya value store hoti hai?
- File tap → AppNavigator → kaunsa FileSystem pick hota hai?
- readText() mein exactly kya fail hota hai?

### Step 3 — Confirm root cause(s)

Har case ke liye:
- Exact file name + line number jahan bug hai
- Exact reason (wrong filesystem chosen? wrong URI? permission not persisted? wrong API call?)
- No guessing — sirf jo code mein confirm ho

### Step 4 — Fix

- Har bug ke liye minimal targeted fix
- Poori file output karo sirf jinhe change kiya
- FileNode canonical shape preserve karo:
  data class FileNode(
      val name: String,
      val path: String,
      val isFolder: Boolean,
      val children: List<FileNode> = emptyList()
  )
- Koi extra refactor nahi, koi naya pattern introduce nahi
- Goal: ZIP aur SAF folder — dono mein har file type
  (.kt .xml .html .py .js .dart .md .json .yaml .gradle etc.)
  bina kisi error ke open aur save ho sake

### Step 5 — Output format

Ek section per bug:
BUG #N
File: <filename>
Line: <line number>
Confirmed cause: <exact reason>
Fix: <complete updated file>
