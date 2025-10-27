#!/usr/bin/env python3
"""
Sprint 1 테스트용 타일 생성 스크립트

일단 지금은 수동으로 타일 만들어서 MinIO에 올리는 용도
나중에 자동화할때는 이 로직을 백엔드나 별도 서비스로 옮길 예정

Usage:
    python3 generate_test_tiles.py <image_path> <image_id>

Example:
    python3 generate_test_tiles.py JPG_Test.jpg test-image-001

Requirements:
    brew install vips
    pip3 install pyvips minio
"""

import sys
import os
import shutil
import time
from pathlib import Path
import pyvips
from minio import Minio
from minio.error import S3Error


def generate_tiles(input_image, output_dir, image_id):
    """
    pyvips로 DZI 타일 생성
    알아본바로는 의료 이미지 처리할때 pyvips가 제일 무난함
    """
    print(f"Loading image: {input_image}")
    
    image = pyvips.Image.new_from_file(input_image, access='sequential')
    
    print(f"  Image size: {image.width} x {image.height}")
    print(f"  Bands: {image.bands}, Format: {image.format}")
    
    tiles_path = Path(output_dir) / image_id
    tiles_path.mkdir(parents=True, exist_ok=True)
    
    print(f"\nGenerating DZI tiles...")
    print(f"  Output: {tiles_path}")
    
    # DZI 피라미드 생성
    # image.dzi (XML 메타데이터) + image_files/ (실제 타일들) 구조로 만들어짐
    base_path = tiles_path / "image"

    image.dzsave(
        str(base_path),
        suffix=".jpg[Q=85]",
        overlap=0,
        tile_size=256,
        depth="onepixel",
        layout="dz",
        properties=False,
        skip_blanks=False
    )
    
    print(f"Tiles generated successfully")
    
    file_count = sum(1 for _ in tiles_path.rglob("*") if _.is_file())
    print(f"  Total files: {file_count}")
    
    return tiles_path


def upload_to_minio(tiles_dir, image_id):
    """
    생성된 타일들 MinIO에 업로드
    """
    print(f"\nConnecting to MinIO...")
    client = resolve_minio_client()
    bucket = os.environ.get("MINIO_BUCKET", "histoflow-tiles")
    
    try:
        if not client.bucket_exists(bucket):
            print(f"  Creating bucket: {bucket}")
            client.make_bucket(bucket)
        else:
            print(f"  Bucket exists: {bucket}")
    except S3Error as e:
        print(f"Error checking/creating bucket: {e}")
        sys.exit(1)
    
    tiles_path = Path(tiles_dir) / image_id
    
    print(f"\nUploading to MinIO bucket: {bucket}")
    file_count = 0
    
    for file_path in tiles_path.rglob("*"):
        if file_path.is_file():
            # 디렉토리 구조 그대로 MinIO에 올림
            relative_path = file_path.relative_to(tiles_path)
            object_name = f"{image_id}/{relative_path}"
            
            client.fput_object(bucket, object_name, str(file_path))
            file_count += 1
            
            if file_count % 100 == 0:
                print(f"  Uploaded {file_count} files...")
    
    print(f"Upload complete: {file_count} files uploaded to MinIO")
    print(f"  View in MinIO Console: http://localhost:9001")


def resolve_minio_client():
    endpoint = os.environ.get("MINIO_ENDPOINT", "localhost:9000")
    access_key = os.environ.get("MINIO_ACCESS_KEY", "minioadmin")
    secret_key = os.environ.get("MINIO_SECRET_KEY", "minioadmin")
    secure = os.environ.get("MINIO_SECURE", "false").lower() in {"1", "true", "yes"}

    return Minio(endpoint, access_key=access_key, secret_key=secret_key, secure=secure)


def prompt_for_input(prompt: str) -> str:
    try:
        return input(prompt).strip()
    except EOFError:
        return ""


if __name__ == "__main__":
    args = sys.argv[1:]

    input_image = args[0] if len(args) >= 1 else ""
    if not input_image:
        input_image = prompt_for_input("Enter the source image path (e.g., CMU-1.tiff): ")

    if not input_image:
        print("Error: Source image path is required.")
        sys.exit(1)

    if not os.path.exists(input_image):
        print(f"Error: Input file not found: {input_image}")
        sys.exit(1)

    image_id = args[1] if len(args) >= 2 else ""
    if not image_id:
        stem = Path(input_image).stem
        sanitized = stem.strip().replace(" ", "-") if stem else ""
        image_id = sanitized or f"tiles-{int(time.time())}"

    output_dir = "./tiles_output"

    print(f"Starting tile generation for image_id='{image_id}'\n")

    # 1. 로컬에 타일 생성
    generate_tiles(input_image, output_dir, image_id)

    # 2. MinIO에 업로드
    upload_to_minio(output_dir, image_id)

    # 3. 로컬 파일 정리 (s3 쓰는게 아니다보니 디스크 공간 아끼려고)
    print(f"\nCleaning up local tiles...")
    shutil.rmtree(output_dir)

    print(f"\nDone! Test your tile serving:")
    print(f"  DZI: http://localhost:8080/api/v1/tiles/{image_id}/image.dzi")
    print(f"  Tile: http://localhost:8080/api/v1/tiles/{image_id}/image_files/0/0_0.jpg")
    print(f"\n  Open frontend: http://localhost:3000/tile-viewer")
