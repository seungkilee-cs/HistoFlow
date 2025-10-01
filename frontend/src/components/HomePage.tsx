import React, { useState, useRef } from 'react';
import '../styles/HomePage.scss';

function HomePage() {
  const [image, setImage] = useState<File | null>(null);
  // Likely won't be needed as real images are to big. Just here for testing
  const [preview, setPreview] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      const file = e.dataTransfer.files[0];
      setImage(file);
      setPreview(URL.createObjectURL(file));
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
      </div>
    </div>
  );
}

export default HomePage;
