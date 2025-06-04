# Important note
The whole app is AI generated with GPT-4.1 model.

# Location Sender

Location Sender is an Android app that allows users to quickly share their current location via WhatsApp. The app features country code selection, live location preview, and a static map image using Geoapify.

## Features
- Select country code from a searchable list
- Enter and validate phone numbers
- Fetch and display current location and address
- Show a static map preview of the location
- Share location details via WhatsApp
- Error logging for troubleshooting

## Architecture & Principles
- **SOLID Principles**: Business logic is separated into manager classes (e.g., `CountryCodeManager`, `LocationManagerWrapper`, `WhatsAppSender`, `LocationPreviewManager`).
- **Single Responsibility**: Each class handles a single concern.
- **Dependency Inversion**: UI depends on interfaces, not concrete implementations.
- **Centralized Error Handling**: All errors are logged via `ErrorLogger`.

## Setup
1. Clone the repository.
2. Add your Geoapify API key to `local.properties`:
   ```
   GEOAPIFY_API_KEY=your_api_key_here
   ```
3. Open the project in Android Studio.
4. Build and run on your device or emulator.

## Testing
- Unit tests can be added for manager classes in `app/src/test/java/`.

## License
This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
