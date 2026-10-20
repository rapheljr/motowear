# Contributing to MotoWear OS

Thank you for your interest in contributing to **MotoWear OS**!

---

## 🛠️ How to Contribute

### 1. Reporting Bugs
- Search existing issues to see if the bug has already been reported.
- If not, open a new issue using the **Bug Report** template.
- Include device details (Wear OS version, watch model, phone OS version).

### 2. Suggesting Features
- Open a new issue using the **Feature Request** template.
- Clearly describe the feature and its use case.

### 3. Submitting Pull Requests
1. Fork the repository and create a feature branch (`git checkout -b feature/my-feature`).
2. Follow Kotlin coding conventions and existing project architectural patterns.
3. Verify all unit tests pass before opening a PR:
   ```bash
   ./gradlew test
   ```
4. Open a Pull Request against the `main` branch with a clear description of your changes.

---

## 🎨 Code Style Guidelines

- Use Kotlin standard coding style.
- Use explicit types when appropriate for domain classes.
- Ensure Jetpack Compose Composables follow standard parameters (`modifier: Modifier = Modifier`).
- Format code with `./gradlew spotlessApply` or Android Studio auto-formatter before committing.

---

## 📜 Code of Conduct

All contributors are expected to adhere to our [Code of Conduct](CODE_OF_CONDUCT.md).
