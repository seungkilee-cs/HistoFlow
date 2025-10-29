import { useState, useRef } from 'react';
import '../styles/HomePage.scss';
import { uploadFileWithPresignedMultipart } from '../utils/upload';

function HomePage() {
  const [_image, setImage] = useState<File | null>(null);
  // Likely won't be needed as real images are to big. Just here for testing
  const [preview, setPreview] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState<number>(0);
  const [uploadError, setUploadError] = useState<string | null>(null);

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      const file = e.dataTransfer.files[0];
      setImage(file);
      setPreview(URL.createObjectURL(file));
      // kick off upload
      startUpload(file);
    }
  };

  const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];
      setImage(file);
      setPreview(URL.createObjectURL(file));
      // kick off upload
      startUpload(file);
    }
  };

  // Upload function with progress updates
  const startUpload = async (file: File) => {
    setUploading(true);
    setUploadError(null);
    setProgress(0);
    try {
      // Shooting for 64MB concurrency with 4 * 16MB parts
      await uploadFileWithPresignedMultipart(file, {
        concurrency: 4, // Number of parallel upload parts... 4 HTTP calls run at the same time
        partSizeHint: 16 * 1024 * 1024, // How big each part should be (16MB).. S3 minimum is 5MB
        onProgress: (uploaded, total) => {
          setProgress(Math.round((uploaded / total) * 100));
        },
      });
      setProgress(100);
    } catch (err: any) {
      setUploadError(err?.message || String(err));
    } finally {
      setUploading(false);
    }
  };

  const handleButtonClick = () => {
    fileInputRef.current?.click();
  };

  return (
    <div className="HomePage">
      <h1>HistoFlow</h1>
      <div
        className="upload-container"
        onDrop={handleDrop}
        onDragOver={handleDragOver}
      >
        <p>Drag & drop an image here</p>
        <button
          type="button"
          onClick={handleButtonClick}
          className="upload-button"
        >
          Select Image
        </button>
        <input
          type="file"
          accept="image/*"
          ref={fileInputRef}
          style={{ display: 'none' }}
          onChange={handleFileChange}
        />
        {preview && (
          <div className="preview-container">
            <img
              src={preview}
              alt="Preview"
              className="preview-image"
            />
          </div>
        )}

        {/* Upload progress indicator */}
        {uploading && (
          <div style={{ marginTop: '1rem', color: '#fff' }}>
            Uploading... {progress}%
          </div>
        )}
        {/* Error message */}
        {uploadError && (
          <div style={{ marginTop: '1rem', color: '#ff6b6b' }}>
            Error: {uploadError}
          </div>
        )}
      </div>
    </div>
  );
}

export default HomePage;
