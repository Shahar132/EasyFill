# Cloud Run Backend

This directory contains the Google Cloud Run backend used by EasyFill.

The backend receives uploaded PDF documents from the Android application, communicates with Azure AI Document Intelligence, extracts structured information, applies field mapping and normalization, and returns the processed data to the Android application.

## Notes

- The source code is provided for reference.
- Sensitive credentials are excluded from this repository.
- Configure the required environment variables before deploying the service.