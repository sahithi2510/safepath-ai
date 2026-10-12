FROM python:3.11-slim

LABEL maintainer="SafePath AI"
LABEL description="SafePath AI FastAPI ML Service"

# Set environment variables
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    ML_HOST=0.0.0.0 \
    ML_PORT=8001

WORKDIR /app

# Create non-root user
RUN addgroup --system safepath && adduser --system --group safepath

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY ./app /app/app
# Create models directory (to be mounted as a volume in prod)
RUN mkdir -p /app/models && chown -R safepath:safepath /app

# Switch to non-root user
USER safepath

EXPOSE 8001

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8001/health')" || exit 1

# Start the application
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001", "--proxy-headers"]
