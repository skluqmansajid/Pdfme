# pdfme

An original Android PDF editor inspired by [KillerPDF](https://github.com/SteveTheKiller/KillerPDF)
(a free, open-source PDF editor for Windows). This app is **not** affiliated with
KillerPDF and shares no code with it — it is a from-scratch Android app written
in Kotlin that brings the core mobile workflow to your phone.

## What it does

- **Open** any PDF from your phone (file picker, or open a PDF from another app)
- **View** pages, swipe through them with previous/next controls
- **Draw** freehand on any page (black, red, blue pen)
- **Sign**: draw your signature with your finger, drag it into position on the page
- **Rotate** pages 90°
- **Delete** pages (pick any pages from a checklist)
- **Save** back to the original file, or **Save as…** a new copy
- **Delete** files from your phone from the recent list

Edits are baked into the PDF itself (drawings and signatures become part of the
page), so the saved file looks the same in any PDF reader. The original file is
never touched until you tap Save.

## Build

Requires Android SDK (API 34) and JDK 17:

```sh
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`. Install it on your
phone by opening the file (allow "install unknown apps" for your browser/files
app when asked).

## Notes

- Minimum Android version: 8.0 (API 26).
- PDF rendering uses Android's built-in `PdfRenderer`; PDF editing uses
  [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache 2.0).
- Editing existing PDF *text* (reflowing/retyping text) is not supported — that
  needs a full desktop-class engine. Drawings, signatures, rotation and page
  management are fully supported.
