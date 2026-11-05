# Album ordering evidence

Crops show only the track rows from Android 15 (API 35) emulator screenshots.
The same three generated local MP3s were imported in order 02, 03, 01; their
embedded track numbers are 01, 02, 03. No user audio or account is involved.

- `original-8.3.2.png`: official Mochy 8.3.2 universal FOSS APK, GitHub Actions run [42037344242](https://github.com/XbibzOfficial777/Mochy-Music/actions/runs/42037344242).
- `candidate.png`: independent debug build of the proposed original-project source, GitHub Actions run [42051106317](https://github.com/XbibzOfficial777/Mochy-Music/actions/runs/42051106317).
- Regression and lint run: [42049625544](https://github.com/XbibzOfficial777/Mochy-Music/actions/runs/42049625544).

The full screenshots and emulator logs were uploaded as Actions artifacts with a 7-day retention period. Cropping is the only image modification.
