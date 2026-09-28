# PUBLISH GUIDE (sirf tumhare liye, ise repo me public mat rakhna)

> **Is file ko publish karne se pehle DELETE kar dena** (ya `.gitignore` me daal dena).
> Isme tumhare private checklist hai, users ke liye nahi.

Tumhara plan: **repo private hi rahega, sirf source-code ka release public hoga.**
Yeh sahi plan hai. Neeche har step order me hai. **Ek step skip mat karo.**

---

## STEP 1. Pehle check karo ki history me secret to nahi hai  (5 min, sabse zaroori)

Kyunki tumhari CI already chal rahi hai, repo GitHub par hai. Apne computer par repo ka folder kholo aur chalao:

```bash
git log --all --oneline -- '*.jks' '*.keystore' 'google-services.json' 'local.properties' '*.pem' '*.p12'
```

- **Kuch bhi print NAHI hua** -> achha, aage badho.
- **Kuch print hua** -> RUKO. Matlab koi secret file kabhi commit hui thi. Tab:
  1. Us keystore ko **naya banao** (purani key ab safe nahi hai).
  2. History saaf karo: `pip install git-filter-repo` phir
     `git filter-repo --invert-paths --path google-services.json --path app/ashuphone-release.keystore`
  3. Naya keystore GitHub Secrets me daalo.

Ek aur check (koi password/token text me to nahi):

```bash
git log --all -p | grep -E "AIza[0-9A-Za-z_-]{30,}|-----BEGIN (RSA |EC )?PRIVATE KEY|ghp_[A-Za-z0-9]{30,}"
```
Kuch print hua to us key ko revoke/rotate karo.

---

## STEP 2. Firebase API key ko restrict karo  (3 min)

Yeh public source ke saath sabse zaroori hai.

1. https://console.cloud.google.com/apis/credentials kholo, project **ashu-phone-07x** chuno.
2. "Android key" par click karo.
3. **Application restrictions -> Android apps** chuno.
4. **Add an item**: package name `com.ashudialer.app` aur apni **release SHA-1**.
   - SHA-1 nikalne ke liye: `keytool -list -v -keystore <tumhara.keystore>`
5. **API restrictions -> Restrict key** chuno, sirf wahi APIs rakho jo app use karti hai
   (Identity Toolkit, Cloud Firestore, Firebase Installations, FCM Registration).
6. Save.

---

## STEP 3. Firebase App Check ON karo  (10 min)  <- asli backend protection

Iske bina koi bhi apna client bana ke tumhare Firebase ko hit kar sakta hai.

1. Firebase Console -> **App Check** -> **Get started**.
2. Apni Android app par click karo -> provider: **Play Integrity** -> Save.
3. Pehle **"Monitor"** mode me chalao, ek-do din dekho ki genuine users fail to nahi ho rahe.
4. Sab theek ho to **Firestore** ke saamne **Enforce** dabao.

> App Check ka code **ab project me add ho chuka hai** (`firebase-appcheck-playintegrity` + init in `AshuDialerApp.kt`).
> Lekin **Enforce tab tak MAT dabana** jab tak: (a) nayi APK ban ke users tak na pahunch jaye, aur (b) Monitor mode
> me Firestore requests "Verified" na dikhne lagein. Pehle Enforce dabaya to puraane app-version wale users bhi block ho jayenge.
> Play Integrity ke liye app ka **Google Play par hona zaroori nahi**, lekin Play Services wale phone chahiye.
> Jo phone Play Services ke bina hain (custom ROM/de-Googled) unhe token nahi milega; unke liye Enforce tab tak band rakho
> jab tak tum decide na kar lo ki unhe support karna hai ya nahi.

---

## STEP 4. Firestore rules deploy karo aur TEST karo  (10 min)

```bash
npm install -g firebase-tools
firebase login
firebase use ashu-phone-07x
firebase deploy --only firestore:rules
```

Phir **do phones** par test karo:
1. Phone A se Phone B ko video call lagao -> ring hona chahiye.
2. Phone B uthaye -> connect hona chahiye.
3. Kisi bhi ek taraf se kaato -> dono taraf band ho.

Agar koi step fail ho to Firebase Console -> Firestore -> Rules -> "Rules playground" me dekho
kaun si line deny kar rahi hai. Puraane rules wapas chahiye to `git log` se purani `firestore.rules` nikaal lo.

---

## STEP 5. Release APK ko phone par test karo  (15 min)

Maine kuch compile nahi kiya, isliye yeh **zaroori** hai. GitHub Actions se bani APK download karke check karo:

- [ ] App khulti hai (Tamper screen NAHI aana chahiye)
- [ ] Call lagti hai aur aati hai
- [ ] **Default dialer** set hota hai (ab auto-redirect nahi hoga, fail hone par message aayega)
- [ ] Call recording chalti hai (Shizuku wali)
- [ ] Video call chalti hai
- [ ] Settings -> About -> "Check for updates" chalta hai
- [ ] Cloud backup / restore chalta hai

**Agar release me kuch crash kare** (debug me sahi, release me nahi) to R8 ka issue hai. Tab `app/proguard-rules.pro`
me us class ke liye `-keep class ... { *; }` line jodni hogi. Crash ka naam mujhe bhej do.

---

## STEP 6. GitHub settings  (5 min)

Repo -> **Settings**:
- **Code security** -> ON karo: *Secret scanning*, *Push protection*, *Private vulnerability reporting*, *Dependabot alerts*.
- **Branches** -> `master` par protection: *Require a pull request*, *Block force pushes*.
- **Actions -> General** -> "Fork pull request workflows" -> *Require approval for all outside collaborators*.

---

## STEP 7. Dependabot PRs ka kya karna hai

Screenshot me jo 4 PR fail hue (`annotation`, `room-compiler`, `kotlinx-coroutines`): **inko merge mat karo, Close karo.**
Naye `dependabot.yml` me major-version bump band kar diya hai, to aisa dobara nahi hoga.
Jo PR **green (pass)** hain (`upload-artifact`, `checkout`, `setup-java`, `coil`) unhe merge kar sakte ho.

---

## STEP 8. "Sirf source release" kaise karna hai

Tumhara repo private hai, isliye source public karne ke 2 tareeke hain:

**Tareeka A (aasan): Naya public repo banao, sirf code daalo (history nahi).**
```bash
# ek fresh folder me, bina .git ke
cp -r Ashudialer AshuDialer-public && cd AshuDialer-public
rm -rf .git PUBLISH_GUIDE.md
git init && git add . && git commit -m "Initial public release"
git remote add origin https://github.com/<tum>/<naya-repo>.git
git push -u origin main
```
Fayda: **puri purani history public nahi hoti**, to STEP 1 ka risk khatam ho jata hai.
Yeh sabse safe hai. **Main yahi recommend karta hu.**

**Tareeka B: Private repo ko hi public kar do.** Isme puri history public ho jayegi, to STEP 1 zaroori hai.

Dono me: `firestore.rules` public ho jayegi. Yeh normal hai, rules secret nahi hote, security unke logic se aati hai.

---

## STEP 9. Public karne ke baad

- README me **official APK ka link** (Releases page) likho.
- Koi tumhara code bina credit ke le to: GitHub par `https://github.com/contact/dmca` se report karo.
  Tumhare har `.kt` file ke upar copyright header hai, wahi tumhara saboot hai.

---

## Yaad rakho: kya secure hai aur kya nahi

| Cheez | Status |
|---|---|
| Update ke time fake APK | Rokta hai (signature check) |
| Modded APK official ke upar install | Android khud rokta hai (alag key) |
| Modded APK ka apni key se chalna | **Nahi rok sakte.** Yeh open source hai. |
| Backend ko fake client se hit karna | App Check ON hone ke baad hi rukega (STEP 3) |
| Code padhna / copy karna | Allowed hai, par GPL me credit dena zaroori |
