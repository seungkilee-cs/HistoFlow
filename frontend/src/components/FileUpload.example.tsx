import React, { useState } from "react";
import axios from "axios";

// defining the shape of the response from our backend's /initiate endpoint
interface InitiateUploadResponse {
  uploadUrl: string;
  objectName: string;
}

const FileUpload: React.FC = () => {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploadProgress, setUploadProgress] = useState<number>(0);
  const [uploadStatus, setUploadStatus] = useState<
    "idle" | "uploading" | "success" | "error"
  >("idle");
  const [errorMessage, setErrorMessage] = useState<string>("");

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    if (event.target.files && event.target.files[0]) {
      setSelectedFile(event.target.files[0]);
      setUploadStatus("idle");
      setUploadProgress(0);
      setErrorMessage("");
    }
  };

  const handleUpload = async () => {
    if (!selectedFile) {
      setErrorMessage("select file to upload first");
      return;
    }

    setUploadStatus("uploading");
    setUploadProgress(0);
    setErrorMessage("");

    try {
      // first request backend for a pre-signed url
      console.log("Requesting pre-signed URL from backend...");
      const initiateResponse = await axios.post<InitiateUploadResponse>(
        "/api/v1/uploads/initiate",
        {
          fileName: selectedFile.name,
          contentType: selectedFile.type,
        },
      );

      const { uploadUrl, objectName } = initiateResponse.data;
      console.log("Received pre-signed URL:", uploadUrl);

      // then upload the file directly to MinIO using the pre-signed url
      console.log("Uploading file directly to MinIO...");
      await axios.put(uploadUrl, selectedFile, {
        headers: {
          "Content-Type": selectedFile.type,
        },
        onUploadProgress: (progressEvent) => {
          const percentCompleted = Math.round(
            (progressEvent.loaded * 100) / (progressEvent.total ?? 1),
          );
          setUploadProgress(percentCompleted);
        },
      });

      console.log("File upload complete.");

      // not explicitly part of the upload, but notify the backend that the upload is complete.
      // Backend can use this status update to trigger the tiling job.
      await axios.post("/api/v1/uploads/complete", { objectName });

      setUploadStatus("success");
    } catch (error) {
      console.error("An error occurred during the upload process:", error);
      setUploadStatus("error");
      setErrorMessage("Upload failed. Please try again.");
    }
  };

  return (
    <div>
      <h2>Upload Image</h2>
      <input type="file" onChange={handleFileChange} />
      <button
        onClick={handleUpload}
        disabled={!selectedFile || uploadStatus === "uploading"}
      >
        {uploadStatus === "uploading"
          ? `Uploading... ${uploadProgress}%`
          : "Upload"}
      </button>

      {uploadStatus === "uploading" && <div>{uploadProgress}%</div>}

      {uploadStatus === "success" && <p>Upload successful!</p>}
      {uploadStatus === "error" && <p>{errorMessage}</p>}
    </div>
  );
};

export default FileUpload;
