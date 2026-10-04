FROM python:3.11-slim
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends postgresql-client && rm -rf /var/lib/apt/lists/*
COPY backend/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY backend/app ./app
COPY staff-portal ./staff-portal
ENV STAFF_PORTAL_DIR=/app/staff-portal
RUN useradd --create-home foodbridge && mkdir data /backups && chown foodbridge data /backups
USER foodbridge
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--no-access-log"]
