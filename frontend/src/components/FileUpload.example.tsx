import React, { useEffect, useRef, useState } from "react";
import axios, { AxiosProgressEvent } from "axios";

// defining the shape of the response from our backend's /initiate endpoint
interface InitiateUploadResponse {
  uploadUrl: string;
  objectName: string;
  imageId: string;
  datasetName: string;
}

const FileUpload: React.FC = () => {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [datasetName, setDatasetName] = useState<string>("");
  const [uploadProgress, setUploadProgress] = useState<number>(0);
  const [uploadStatus, setUploadStatus] = useState<
    "idle" | "uploading" | "success" | "error"
  >("idle");
  const [errorMessage, setErrorMessage] = useState<string>("");
  const [latestResponse, setLatestResponse] = useState<InitiateUploadResponse | null>(null);
  const [tilingStatus, setTilingStatus] = useState<
    "idle" | "processing" | "completed" | "failed"
  >("idle");
  const [tilingMessage, setTilingMessage] = useState<string>("");
  const pollTimerRef = useRef<NodeJS.Timeout | null>(null);

  const clearPolling = () => {
    if (pollTimerRef.current) {
      clearInterval(pollTimerRef.current);
      pollTimerRef.current = null;
    }
  };

  useEffect(() => {
    return () => {
      clearPolling();
    };
  }, []);

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    if (event.target.files && event.target.files[0]) {
      setSelectedFile(event.target.files[0]);
      setUploadStatus("idle");
      setUploadProgress(0);
      setErrorMessage("");
      setTilingStatus("idle");
      setTilingMessage("");
      clearPolling();
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
    setTilingStatus("idle");
    setTilingMessage("");
    clearPolling();

    try {
      // first request backend for a pre-signed url
      console.log("Requesting pre-signed URL from backend...");
      const initiateResponse = await axios.post<InitiateUploadResponse>(
        "/api/v1/uploads/initiate",
        {
          fileName: selectedFile.name,
          contentType: selectedFile.type,
          datasetName: datasetName || undefined,
        },
      );

      const { uploadUrl, objectName, imageId, datasetName: resolvedName } =
        initiateResponse.data;
      setLatestResponse(initiateResponse.data);
      console.log(
        "Received pre-signed URL:",
        { uploadUrl, objectName, imageId, datasetName: resolvedName },
      );

      // then upload the file directly to MinIO using the pre-signed url
      console.log("Uploading file directly to MinIO...");
      await axios.put(uploadUrl, selectedFile, {
        headers: {
          "Content-Type": selectedFile.type,
        },
        onUploadProgress: (progressEvent: AxiosProgressEvent) => {
          const percentCompleted = Math.round(
            (progressEvent.loaded * 100) / (progressEvent.total ?? 1),
          );
          setUploadProgress(percentCompleted);
        },
      });

      console.log("File upload complete.");

      // not explicitly part of the upload, but notify the backend that the upload is complete.
      // Backend can use this status update to trigger the tiling job.
      await axios.post("/api/v1/uploads/complete", {
        objectName,
        imageId,
        datasetName: resolvedName,
      });

      setUploadStatus("success");
      setTilingStatus("processing");
      setTilingMessage("Tiling job accepted. Polling status...");

      clearPolling();
      pollTimerRef.current = setInterval(async () => {
        try {
          const statusResponse = await axios.get(`/api/v1/tiles/${imageId}/status`);
          const status = statusResponse.data?.status as string | undefined;
          const message = statusResponse.data?.message as string | undefined;

          if (!status) {
            return;
          }

          if (status === "completed") {
            setTilingStatus("completed");
            setTilingMessage(message ?? "Tiles are ready.");
            clearPolling();
          } else if (status === "processing") {
            setTilingStatus("processing");
            setTilingMessage(message ?? "Tiling in progress...");
          } else if (status === "not_found") {
            setTilingStatus("failed");
            setTilingMessage("Upload not found. Please retry.");
            clearPolling();
          } else {
            setTilingMessage(message ?? `Status: ${status}`);
          }
        } catch (statusError) {
          console.error("Error polling tiling status:", statusError);
          setTilingStatus("failed");
          setTilingMessage("Error polling tiling status. Check console for details.");
          clearPolling();
        }
      }, 5000);
    } catch (error) {
      console.error("An error occurred during the upload process:", error);
      setUploadStatus("error");
      setErrorMessage("Upload failed. Please try again.");
      setTilingStatus("failed");
      setTilingMessage("Upload failed before tiling could start.");
      clearPolling();
    }
  };

  return (
    <div>
      <h2>Upload Image</h2>
      <input type="file" onChange={handleFileChange} />
      <input
        type="text"
        placeholder="Dataset name (optional)"
        value={datasetName}
        onChange={(event) => setDatasetName(event.target.value)}
      />
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
      {tilingStatus !== "idle" && (
        <p>
          Tiling status: {tilingStatus}
          {tilingMessage ? ` – ${tilingMessage}` : ""}
        </p>
      )}
      {latestResponse && (
        <div>
          <p>Image ID: {latestResponse.imageId}</p>
          <p>Object name: {latestResponse.objectName}</p>
          <p>Dataset name: {latestResponse.datasetName}</p>
        </div>
      )}
    </div>
  );
};

export default FileUpload;
