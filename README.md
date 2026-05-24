# Sync360

Sync360 is a lightweight, cloud-free, multiplatform ecosystem designed to solve the clunky friction of moving clipboards, media, and files across multiple personal devices. Built using Kotlin Multiplatform (KMP) and Compose Multiplatform (CMP), it bridges Android, iOS, and Desktop (Windows, Mac, Linux) into a single, cohesive, ultra-fast environment.

---

## 🎯 The Core Vision (The Original Idea)

The inspiration for Sync360 stems from a frustratingly common real-world problem: **the clunky friction of cross-device sharing.** Often, you use a speech-to-text app on your mobile phone to convert voice to text effortlessly. However, because you are actively working on your PC, you need that text there. To bridge this gap, you resort to inefficient workarounds—opening WhatsApp, sending the text to yourself or a group chat, opening WhatsApp Web on the PC, and then copying it into your target application. The same painful workflow applies when you need to quickly move a recent photo, video, or document from your mobile gallery to your desktop. You have to open cloud storage apps, wait for slow syncs, or log into third-party interfaces.

**Sync360 transforms this entirely.** It establishes an instant local connection between an app running on your PC and your mobile devices. Everything updates fluidly without relying on third-party cloud storage. 
* **Universal Clipboard:** Whenever you copy text or data on your PC, it instantly appears on your mobile app. If you copy something on your mobile, it instantly broadcasts to your desktop and all other connected ecosystem devices.
* **Instant Drag & Drop:** Drop any file or asset directly into the application interface, and it instantly distributes to all other connected instances with high, uncompressed quality. 
* **Device Profiles & Organization:** The app acts as an elegant ledger separated by specific device streams. You can click on a specific device profile to view exactly what that particular machine has recently copied or made available, neatly categorized for immediate use.

---

## 🚀 Key Architecture & Product Features

### 1. The Persistent Floating Dock (UX Masterstroke)
Instead of forcing users to explicitly open a heavy app to sync files, Sync360 introduces a persistent, lightweight **Floating Dock** on the edge of the mobile screen (utilizing Android's `SYSTEM_ALERT_WINDOW` permissions). 
* **Instant Access Overlay:** Tapping this floating bubble opens an elegant dock that lets you see the current live clipboard text and synced assets from other devices without leaving your current app.
* **Seamless Android Clipboard Bypass:** Modern Android versions block background apps from reading the system clipboard for privacy. The Floating Dock elegant bypasses this: when a user copies text in an app like Chrome and taps the dock, the window gains focus, immediately allowing `ClipboardManager` to safely pull the fresh clipboard text and broadcast it instantly.
* **On-the-Fly Drag Target:** Users can long-press a photo inside their native gallery, drag it directly into the overlay box, and drop it to trigger a direct system-wide broadcast.

### 2. Device Profiles & Categorized Filtering
Sync360 avoids a messy, confusing chronological timeline of mixed files. Instead, the UI is organized by **Device Profiles**:
* Inside the app or the expanded dock, users can choose a specific source profile (e.g., *"My Windows Desktop"* or *"6th-Sem Mac"*).
* Selecting a profile loads that specific machine's historical logs, prioritizing data type hierarchy: **Text Clipboards first**, followed by **Media (Images & Videos)**, and finally **Documents**.

### 3. Intelligent "Lazy Loading" Thumbnail Engine
Broadcasting gigabytes of raw, high-resolution 4K videos or raw images automatically across multiple local devices would cripple battery life, chew through local RAM, and jam network channels. Sync360 utilizes an optimized multi-tier synchronization strategy:
* **Metadata & Previews Only:** When the application starts, it reads the 20 most recent images, 20 most recent videos, and 20 most recent documents from the host device. It does **not** transfer the heavy original assets. Instead, it extracts the file metadata (name, size, extension) and utilizes Android's `ContentResolver.loadThumbnail()` to extract a highly optimized, low-kilobyte thumbnail preview bitmap.
* **On-Demand Fetching:** These lightweight previews are immediately broadcast to all connected devices. The recipient devices render the thumbnails with an explicit `"Not Fetched"` indicator. The moment a user clicks on an item, a dedicated network socket fetches the high-quality raw file on demand.

### 4. Hybrid Network Transport (LAN + WebRTC)
Sync360 is built to run entirely peer-to-peer without risking data privacy on external cloud servers, operating intelligently based on network conditions:
* **Local LAN Network Sync (Same Wi-Fi):** Devices discover each other effortlessly without requiring manual IP entry using **Network Service Discovery (NSD)**. Once paired, data transfers execute over high-speed direct **WebSockets and HTTP servers embedded inside the apps** (powered by Ktor). On a typical local Wi-Fi router, transfers bypass internet speed limits completely, achieving blazing local throughput (often 30–100+ MB/s) to shift gigabyte files in seconds.
* **Cross-Network Sync (Cellular / Direct Internet):** If a mobile device moves onto a 4G/5G cellular data network while the PC remains on home Wi-Fi, traditional IP socket binding fails due to strict router firewalls (NAT). Sync360 implements **WebRTC Data Channels**. Utilizing lightweight STUN/TURN servers strictly for discovery and hole-punching, it builds an encrypted, cloud-free direct peer-to-peer bridge to pass metadata and file chunks directly between cellular and remote networks.

---

## 🛠️ The Tech Stack

Sync360 is built with a highly cohesive Kotlin ecosystem to share up to 90% of business, networking, and data management layers across platforms while rendering smooth, native user interfaces.

| Architecture Layer | Technology Selection | Implementation Strategy |
| :--- | :--- | :--- |
| **Cross-Platform Engine** | **Kotlin Multiplatform (KMP)** | Unifies data streams, network bindings, and system states into a single shared core logic module. |
| **UI Rendering Framework** | **Compose Multiplatform (CMP)** | Deploys identical, reactive, highly performant Declarative UIs natively to Android, iOS, and Desktop platforms. |
| **Local Persistence Database** | **Room KMP** | Manages relational tables for device IDs, historical clipboards, metadata caching, and transfer states cleanly using native Room architectures. |
| **Networking & Async I/O** | **Ktor (Client & Server)** | Embedded Ktor servers handle direct local LAN file pipelines; Ktor Client handles WebSocket orchestration. |
| **Asynchronous Streaming** | **Kotlin Coroutines & Flows** | Powers non-blocking reactive background operations, transfer tracking, and immediate UI state emissions. |
| **Data Parsing Engine** | **kotlinx.serialization** | Encodes and decodes complex payload metadata arrays into super-dense JSON frames over network sockets. |

---

## 🗄️ Database Architecture (Room Schema)

To power the device-driven UI, the shared Room Database uses a clean relational structure:

### 1. `DeviceEntity` (Tracks connected hardware)
* `deviceId`: `String` (Primary Key - Unique hardware UUID)
* `deviceName`: `String` (e.g., "Main Desktop", "Android Phone")
* `deviceType`: `String` (ANDROID, IOS, DESKTOP)
* `lastActiveTimestamp`: `Long`

### 2. `SharedItemEntity` (Caches metadata and sync logs)
* `itemId`: `String` (Primary Key - Composite hash)
* `originDeviceId`: `String` (Foreign Key referencing `DeviceEntity.deviceId`)
* `categoryType`: `String` (TEXT, MEDIA, DOCUMENT)
* `mimeType`: `String` (e.g., "text/plain", "image/jpeg", "video/mp4")
* `metaContent`: `String` (Stores plain clipboard text OR local file paths, file sizes, and names)
* `thumbnailBytes`: `Blob?` (Nullable - holds the ultra-compressed byte array of the preview icon)
* `syncState`: `String` (THUMBNAIL_ONLY, DOWNLOADING, FULLY_DOWNLOADED)
* `timestamp`: `Long`

---

## 🗺️ Execution Roadmap & Milestones

To prevent scope creep, construction is split into clear development increments:

### Phase 1: The Local Text Bridge (MVP)
* Configure the base KMP/CMP multiplatform project template for Android and Desktop targets.
* Set up a hardcoded IP connection using an embedded Ktor HTTP/WebSocket server.
* Verify successful text emissions: press a button on Android, see the text instantaneously display on the Desktop app shell.

### Phase 2: System Clipboard & Floating Dock
* Implement native platform clipboard listeners via expect/actual hooks.
* Build the Android Foreground Service and create the floating overlay container via `SYSTEM_ALERT_WINDOW`.
* Connect focus-detection to grab clipboard text the instant a user taps the dock icon.

### Phase 3: Auto-Discovery & Profile Architecture
* Implement Network Service Discovery (NSD) to drop manual IP text inputs. Allow devices to automatically handshake when joining the same Wi-Fi.
* Build the Room KMP database layer.
* Design the Compose Multiplatform UI supporting tabbed Device Profiles with category groupings (Text -> Media -> Docs).

### Phase 4: Media Lazy-Loading & WebRTC Extension
* Integrate `ContentResolver.loadThumbnail` on Android and system preview hooks on Desktop to load the 20 most recent assets.
* Implement the chunked file-streaming engine inside Ktor for on-demand downloading upon asset selection.
* Wire up WebRTC data infrastructure to support seamless connection handovers when moving from local Wi-Fi out onto a cellular 4G network.
