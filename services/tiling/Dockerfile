# --- Step 1: Define the Base Environment ---
# offical slim python image for a minimal Linux OS with python pre-installed
FROM python:3.12-slim

# set the working directory inside the container
WORKDIR /app

# set environment variables to prevent python from writing .pyc files and to buffer output
ENV PYTHONDONTWRITEBYTECODE 1
ENV PYTHONUNBUFFERED 1

# --- Step 2: Install System Dependencies ---
# this is the equivalent of "brew install vips". we use apt-get, the package manager for the Debian Linux environment inside the container.
RUN apt-get update && \
    apt-get install -y \
    build-essential \
    libvips-dev && \
    # clean up the cache to keep the image size small
    rm -rf /var/lib/apt/lists/*

# --- Step 3: Install Python Dependencies ---
# copy the requirements file first. 
# docker caches this layer, so it will only re-install packages if the requirements.txt file changes. this is a key optimization.
COPY requirements.txt .

# install the Python packages into the environment
RUN pip install --no-cache-dir -r requirements.txt

# --- Step 4: Copy Application Code ---
# copy the source code of the service into the container
COPY ./src ./src

# --- Step 5: Define the Runtime Command ---
# tell Docker what command to run when the container starts.
# this will start our FastAPI server. the host "0.0.0.0" makes it accessible from outside the container.
CMD ["uvicorn", "src.main:app", "--host", "0.0.0.0", "--port", "8000"]