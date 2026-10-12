# 📱 MoveMate

An Android fitness tracking app that uses the phone's built-in sensors to track daily steps and detect sleep, with all data stored locally so it works offline.

## 🔗 Links

* 🎬 [Demo video]( https://youtu.be/rcW-oU9Vq8o)


## ✨ Features

* **Step tracking** using the Android step counter sensor (`TYPE_STEP_COUNTER`)
* **Sleep detection** based on accelerometer data
* **Offline support**, with structured local data persistence using Room (SQLite)

## 🛠️ Tech Stack

| Area | Technology |
|------|------------|
| Language | Kotlin |
| Architecture | MVVM |
| Database | Room (SQLite) |
| Sensors | Step counter, accelerometer |
| Platform | Android |

## 🏗️ Architecture

MoveMate follows the **MVVM** pattern to keep the UI separate from the business logic:

* **View:** activities/fragments that display data and handle user input
* **ViewModel:** holds UI state and exposes data to the view
* **Model:** Room database, DAOs and data classes

## 🚀 Running the Project

1. Clone the repo:
```
   git clone https://github.com/andozaksu/movemate.git
```
2. Open the project in **Android Studio**.
3. Let Gradle sync, then run it on an emulator or a physical device.

> Step counting and sleep detection rely on motion sensors, so a physical device gives the best results.

## 👤 Author

**And Ozaksu**
[LinkedIn](https://www.linkedin.com/in/and-ozaksu-8241a7296) · [Email](mailto:andozaksu@gmail.com)