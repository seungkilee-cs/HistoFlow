# HistoFlow System Design (Database): AI-Powered Histopathology Analysis

> Last Updated: 2025-09-28 (Sun)

- Image File
- Analysis
- Classificaion Results

## Core Entities

- Slides
    - slide_id (pk)
    - url (unique)
    - dzi_url (unique)
    - dataset_name
    - created_at
    - updated_at
    - is_active

- Analysis
    - analysis_id (pk)
    - slide_id (fk)
    - created_at
    - updated_at
    - is_active