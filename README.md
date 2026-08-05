# EasyFill
<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white">
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white">
  <img src="https://img.shields.io/badge/Firebase-Firestore-FFCA28?logo=firebase&logoColor=black">
  <img src="https://img.shields.io/badge/Azure-AI%20Document%20Intelligence-0078D4?logo=microsoftazure&logoColor=white">
</p>
<p align="center">
  An accessible Android application that helps people coping with post-traumatic stress disorder (PTSD) complete complex bureaucratic form-filling processes by combining AI-powered document processing, automatic form completion, multimodal distress detection, personalized interface adaptations, and a real-time digital assistant.
</p>
<p align="center">
  <img src="easyfill-main-screen.jpeg"
       alt="EasyFill main screen"
       width="200">
</p>
<p align="center">
  <em>Figure 1. EasyFill – Welcome Screen</em>
</p>
<br>
## Overview
EasyFill is an Android prototype designed to reduce the emotional and cognitive burden associated with completing complex bureaucratic forms.
The application combines intelligent document processing, automatic form completion, multimodal distress detection, accessibility features, and a digital assistant. It extracts structured information from previously completed documents, stores reusable user data, automatically completes relevant fields, monitors possible signs of distress during use, and offers optional interface adaptations.
The system was designed primarily for people coping with PTSD who may experience difficulty maintaining concentration, understanding complex instructions, repeatedly entering the same information, or completing lengthy bureaucratic procedures.
> **EasyFill is a supportive accessibility prototype. It is not a medical diagnostic tool and does not replace professional medical, psychological, legal, or administrative assistance.**
---
## Table of Contents
- [The Problem](#the-problem)
- [The Solution](#the-solution)
- [Main Features](#main-features)
- [System Architecture](#system-architecture)
- [System Components](#system-components)
- [Application Logic](#application-logic)
- [Document Processing Flow](#document-processing-flow)
- [Multimodal Distress Detection](#multimodal-distress-detection)
- [Digital Assistant](#digital-assistant)
- [Accessibility and Personalization](#accessibility-and-personalization)
- [Main Use Cases](#main-use-cases)
- [Application Screens](#application-screens)
- [Technology Stack](#technology-stack)
- [Recommended Repository Structure](#recommended-repository-structure)
- [Installation and Setup](#installation-and-setup)
- [Configuration](#configuration)
- [Testing](#testing)
- [User Research and Evaluation](#user-research-and-evaluation)
- [Privacy and Security](#privacy-and-security)
- [Current Limitations](#current-limitations)
- [Future Development](#future-development)
- [Contributors](#contributors)
- [License](#license)
---
## The Problem
People coping with PTSD are often required to complete complex bureaucratic procedures with organizations such as the Israeli Ministry of Defense and the National Insurance Institute to obtain recognition, submit requests, and exercise their rights.
These procedures may require sustained concentration, repeated data entry, interpretation of complex instructions, document collection, and completion of long forms. The resulting cognitive and emotional burden may cause users to pause or abandon the process.
User research conducted during the project highlighted several recurring difficulties:
- Re-entering information that had already appeared in previous documents.
- Difficulty understanding complex wording and form instructions.
- Too many actions or choices appearing on one screen.
- Difficulty maintaining concentration during long processes.
- Emotional overload, pressure, or physical signs of distress.
- A need for clearer guidance and greater control over the interface.
---
## The Solution
EasyFill addresses these challenges through five connected capabilities:
1. **Structured document information extraction**  
   Previously completed PDF documents are analyzed and converted into reusable structured data.
2. **Automatic form completion**  
   Extracted information is mapped to the application's data model and reused to complete future forms.
3. **Multimodal distress detection**  
   Voice features, facial indicators, hand movements, and form-interaction behavior are analyzed to detect deviations from the user's personal baseline.
4. **Digital assistance**  
   When sustained distress is detected, the digital assistant offers optional guidance and interface adaptations.
5. **Accessibility and personalization**  
   Users can adjust colors, text size, audio guidance, music, and other interface settings.
---
## Main Features
### Intelligent Document Processing
- Uploading previously completed PDF documents.
- Hebrew text extraction.
- Detection of document layout, fields, tables, and selection elements.
- Structured field mapping using regular expressions and mapping rules.
- Data normalization before storage.
- Reuse of extracted information in future forms.
### Automatic Form Completion
- Retrieval of previously stored personal information.
- Matching stored values to relevant form fields.
- Automatic completion of known fields.
- Manual verification and editing by the user.
- Saving form progress after interruptions.
### Multimodal Distress Detection
- Voice-based feature analysis.
- Facial and eye-movement analysis.
- Hand-motion analysis using motion sensors.
- Form-interaction behavior analysis.
- Personalized baseline comparison.
- Weighted fusion of available information channels.
- Confirmation across consecutive time windows before alerting.
### Digital Assistant
- Context-aware distress alerts.
- Calming and guiding messages.
- Suggested accessibility adaptations.
- One-tap actions.
- Ability to dismiss recommendations.
- Ability to undo an applied change.
- Direct navigation to manual settings.
- Prevention of repeated recommendations within a short period.
### Accessibility
- Adjustable text size.
- Alternative interface color themes.
- Contrast-aware interface design.
- Large and clearly identifiable interactive elements.
- Text-to-Speech support.
- Speech-to-Text support.
- TalkBack compatibility.
- Reduced number of primary actions per interface view.
- Clear navigation and progress indication.
---
## System Architecture
<!-- Add the complete architecture diagram directly below this comment -->
<p align="center">
  <img src="architecture easyfill.png" alt="EasyFill system architecture" width="700">
</p>
**Figure: EasyFill system architecture**
The system consists of the following main layers:
1. **Android client application**  
   Manages the user interface, form completion, accessibility settings, sensors, speech interaction, and digital assistant.
2. **Firebase services**  
   Handle authentication, user data, structured extracted information, application settings, and form progress.
3. **Google Cloud Run middleware**  
   Acts as a secure backend layer between the Android application and Azure AI Document Intelligence. It receives documents, sends processing requests, receives the extracted output, and returns controlled results to the application.
4. **Azure AI Document Intelligence**  
   Processes uploaded documents and extracts text, document layout, tables, key-value relationships, and form elements.
5. **Mapping and normalization layer**  
   Converts the general Azure output into the internal EasyFill data model using Regex rules, field aliases, mapping logic, and normalization.
6. **Multimodal distress-detection layer**  
   Collects available indicators, generates separate channel scores, calculates a weighted overall score, and confirms sustained distress.
7. **Digital-assistant decision layer**  
   Receives the distress status, application mode, current screen, available actions, and recent recommendation history before selecting an appropriate message or adaptation.
---
## System Components
### Android Application
The Android application is the primary user-facing component. It provides:
- Authentication and user onboarding.
- Document selection and upload.
- Form selection and completion.
- Speech input and audio guidance.
- Accessibility settings.
- Progress tracking.
- Sensor collection.
- Digital-assistant interaction.
### Firebase
Firebase is used for cloud-based application services such as:
- User authentication.
- Storage of user-specific structured information.
- Saving form progress.
- Saving accessibility preferences.
- Storing document-processing results.
- Separating each user's information through access-control rules.
### Cloud Run Middleware
Cloud Run provides a controlled communication layer between the application and Azure.
Its responsibilities include:
- Receiving document-processing requests.
- Validating request data.
- Communicating with Azure AI Document Intelligence.
- Protecting service credentials from exposure inside the Android application.
- Receiving and processing Azure responses.
- Returning only the required output to the client.
- Applying mapping, normalization, or additional server-side logic where required.
### Azure AI Document Intelligence
Azure AI Document Intelligence analyzes uploaded documents and returns:
- Extracted text.
- Paragraphs and text regions.
- Key-value relationships.
- Tables, rows, columns, and cells.
- Selection marks such as checkboxes.
- Coordinates and layout information.
- Reading-order information.
### Field Mapping and Normalization
The raw Azure response is not stored directly as the final user profile.
The processing layer:
1. Searches for expected field names and known variations.
2. Uses Regex patterns where required.
3. Matches extracted labels to internal field identifiers.
4. Resolves multiple possible names for the same field.
5. Normalizes values such as phone numbers, dates, addresses, and identifiers.
6. Organizes the values into predefined data categories.
7. Stores the resulting structured model in Firebase.
### Distress-Detection Engine
The distress-detection engine combines independent information channels:
- Voice.
- Facial indicators.
- Hand movements.
- Form-interaction behavior.
Each available channel produces a score. The final score depends on the current application mode and only includes valid and available measurements.
### Digital Assistant
The digital assistant does not require the user to formulate free-text commands. It receives the current distress status and application context, then selects an appropriate recommendation from predefined actions.
---
## Application Logic
The general application flow is:
```text
User authentication
        ↓
Document upload or form selection
        ↓
Structured information retrieval
        ↓
Automatic field completion
        ↓
User reviews or edits information
        ↓
Available distress indicators are collected
        ↓
A score is calculated for each available channel
        ↓
Channel scores are combined according to the current mode
        ↓
Confirmation checks whether distress remains elevated
        ↓
The digital assistant displays a non-intrusive alert
        ↓
The user may open, dismiss, apply, undo, or manually modify an adaptation

---

## Testing

The multimodal distress-detection evaluation is maintained in a dedicated repository separate from the Android application.

### EasyFill – Distress Detection Evaluation Repository

Repository:

https://github.com/Shahar132/EasyFill-Testing

The testing repository contains the complete evaluation environment used during the development of EasyFill's distress-detection component, including:

- Jupyter Notebook used to execute the complete evaluation pipeline.
- Python implementation of the distress-detection evaluation.
- CSV datasets collected during the experimental evaluation.
- Feature processing and scoring logic.
- Performance evaluation of the multimodal distress-detection algorithm.
- Analysis used to validate the distress scoring approach.

### Running the Evaluation

1. Clone the testing repository.
2. Open the project using **Jupyter Notebook** or **JupyterLab**.
3. Install the required Python dependencies.
4. Open the provided notebook (`.ipynb`).
5. Execute the notebook cells sequentially from top to bottom.

The notebook automatically loads the included CSV datasets, performs the preprocessing and evaluation steps, and reproduces the distress-detection analysis used during the development of EasyFill.

> **Note:** GitHub renders Jupyter Notebook (`.ipynb`) files directly in the browser, allowing the notebook to be viewed without additional tools. To execute the notebook, inspect outputs interactively, or reproduce the experimental evaluation, it should be opened locally using **Jupyter Notebook** or **JupyterLab**. Opening the notebook in environments such as Android Studio may display the notebook as its underlying JSON file rather than as an interactive notebook.
