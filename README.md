# Good Alarm

A personal Android app: tasks, a calendar and loud alarms in one place. Data stays on the phone.

## Run it on your phone

1. Install **Android Studio** from developer.android.com/studio.
2. Unzip this folder, then in Android Studio choose **File → Open** and pick the `GoodAlarm` folder.
3. Wait for "Gradle sync" to finish. The first time downloads tools, which takes 5–15 minutes.
4. On the phone, turn on **Developer options**: go to Settings → About phone and tap *Build number* 7 times. Then go to Developer options and turn on **USB debugging**.
5. Plug the phone in by USB, accept the "Allow USB debugging" prompt, and press the green **▶ Run** button.

## Install a smooth release build

Debug builds feel slower. For daily use:

1. Open **View → Tool Windows → Build Variants** and set `app` to **release**. Then press ▶ Run, or use **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
2. Or, in the Terminal tab at the bottom of Android Studio, run:
   `./gradlew assembleRelease` (on Windows: `gradlew assembleRelease`)
3. The APK is at `app/build/outputs/apk/release/app-release.apk`. Copy it to the phone and open it to install. It is already signed, so no extra setup is needed.
## First launch

Red cards at the top of the task list show anything that would stop alarms from ringing. Tap **Fix** on each one:

- **Notifications:** needed to show the alarm.
- **Full-screen alarms:** needed to show the ringing screen over the lock screen.
- **Exact alarms:** needed to ring on the exact minute.
- **Battery limits off:** stops the phone from killing alarms. On Xiaomi, Oppo, Vivo and Realme phones, also set Settings → Apps → Good Alarm → Battery to **No restrictions** and allow **Autostart**.

## Test checklist

- [ ] Set an alarm 2 minutes ahead, lock the phone, switch to silent. It rings loudly with the full-screen screen.
- [ ] Snooze: it rings again 5 minutes later.
- [ ] Ignore it: it re-rings every 5 minutes, 5 rings total, then shows "missed".
- [ ] A daily 9 PM alarm moves to tomorrow after you dismiss it.
- [ ] Restart the phone: alarms still ring.
- [ ] Pick a custom sound: it plays instead of the default.

## How the code is organised

| File | What it does |
| --- | --- |
| `data/Item.kt` | The one item type (task + calendar entry + alarm) |
| `data/AppDatabase.kt`, `ItemDao.kt` | Local database on the phone (Room) |
| `alarm/AlarmScheduler.kt` | Sets Android alarm clocks; snooze and re-ring rules |
| `alarm/AlarmService.kt` | Plays the loud sound, vibrates, handles Snooze / Dismiss / Done |
| `alarm/AlarmActivity.kt` | Full-screen ringing screen |
| `alarm/Receivers.kt` | Fires alarms; puts them back after a reboot |
| `ui/TaskList.kt` | Overdue / Today / Upcoming / Done list |
| `ui/Calendar.kt` | Month calendar with dots on busy days |
| `ui/EditItemDialog.kt` | Add / edit screen |
| `MainActivity.kt` | App start and the permission cards |

## Next ideas (from the PRD)

Location alarms (500 m), browser companion with Firebase, quick-add, subtasks, home-screen widget.
