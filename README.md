# ePOS T2K (new Android app)

A new Android app for waiters. It works the same way as the old ePOS app and talks to **ePOS Mobile Server** on the main ePOS computer. The server, database and printers stay exactly as they are; only the phone app is new.

- Written in Kotlin with Jetpack Compose (Google's current standard for Android apps)
- Runs on Android 8.0 and newer, on phones and tablets
- Has a different app name and package (`com.epos.waiter`), so it can be installed **alongside** the old app while you test

## What's new compared with the old app

| | Old app | ePOS T2K |
|---|---|---|
| Connecting | Type the IP address | **Finds the ePOS computer on the Wi-Fi by itself**. Typing the address still works |
| Opening the app | Asked for the address again after problems | Remembers the computer and reconnects. If the address changed, it searches the Wi-Fi again |
| Tablets | Phone layout stretched | Menu and order **side by side** |
| Tables screen | Manual refresh | Refreshes every 15 seconds and has a colour legend |
| Item search | Separate screen | Search box above the menu |
| Order lines | | Each line shows "new" or "sent", the original price when discounted, and cancelled lines in red |
| Newer Android | Can fail on Android 9+ (blocks plain-HTTP connections) | Built for Android 15 |

## Same rules as ePOS.exe

The order rules are copied from `frmKOT.vb`, so the kitchen and the bills get the same data as before:

- **Combo items:** pick one item from each sub-section. Sub-item prices are added to the main item.
- **Comment** and **Position** (seats 1–16) are added under the item, the same way as on the PC. If the profile has "Position" switched on, the Position picker opens after each item.
- **Delete** removes lines that haven't been sent yet. **Cancel item** is for lines already sent: the kitchen gets a "Cancel Item" ticket with the next KOT.
- **Price** sets a new price for one item. **% Disc** gives a percentage discount on whole sections.
- **KOT** saves the order and prints it in the kitchen. **INVOICE** saves the order and asks for the bill (if bills from phones are allowed in the server settings).
- Tables are locked while open, so two phones can't edit the same table. They are released when you leave.

## Build the APK (Android Studio)

1. Install **Android Studio** (the free current version from developer.android.com).
2. **File → Open** and choose the `ePOS_Android` folder.
3. Wait for "Gradle sync" to finish. The first time, it downloads Gradle and the libraries, which takes a few minutes.
   If Android Studio says the Gradle wrapper is missing, accept its offer to create it, or use **File → Sync Project with Gradle Files**.
4. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
5. Click **locate** in the message that appears. The file is `app/build/outputs/apk/debug/app-debug.apk`.

If the build shows errors, send a screenshot of the **Build** window and they'll be fixed.

For a final version to give to all phones: **Build → Generate Signed App Bundle / APK → APK**, then create a key and keep the key file safe. Updates must be signed with the same key.

## Install on a phone

1. Copy `app-debug.apk` to the phone (WhatsApp to yourself, USB or Google Drive).
2. Open it and allow "Install unknown apps" when Android asks.
3. Open **ePOS T2K**. It searches the Wi-Fi and connects to the ePOS computer.

## Server update needed

Update **ePOS Mobile Server** to the latest version (in the same zip). It adds a small `hello.php` reply so the app can show the computer's name. Discovery also works with the older server build; it just shows "ePOS computer" instead of the name.

The phone must be on the **same Wi-Fi** as the ePOS computer, and the firewall rule from the server window (**Allow phones**) must be in place.

## Project layout

```
app/src/main/java/com/epos/waiter/
  MainActivity.kt        app start, screen switching
  AppViewModel.kt        everything the screens do (server calls, login, tables, order)
  data/Models.kt         JSON from the server
  data/Network.kt        server calls, Wi-Fi discovery, saved settings
  data/Order.kt          order rules (combo, comment, position, delete, cancel, discounts)
  ui/StartScreens.kt     connect + login
  ui/TablesScreen.kt     rooms, tables, covers, change / join tables
  ui/OrderScreen.kt      menu, order, KOT / INVOICE and their pop-ups
  ui/MoreScreens.kt      expenses, restaurant profile, change password
```
