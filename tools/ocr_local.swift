#!/usr/bin/env swift
import Foundation
import Vision
import AppKit

// 本地截图 OCR 辅助工具（macOS 自带 Vision framework，无需额外安装）。
// 用法: swift tools/ocr_local.swift <图片路径>
// 输出: 每行 "y,x,width,height<TAB>text"，坐标按图片像素。

guard CommandLine.arguments.count >= 2 else {
    FileHandle.standardError.write("usage: ocr_local.swift <image>\n".data(using: .utf8)!)
    exit(2)
}
let path = CommandLine.arguments[1]
guard let image = NSImage(contentsOfFile: path),
      let cgImage = image.cgImage(forProposedRect: nil, context: nil, hints: nil) else {
    FileHandle.standardError.write("cannot load image: \(path)\n".data(using: .utf8)!)
    exit(3)
}

let request = VNRecognizeTextRequest()
request.recognitionLevel = .accurate
request.usesLanguageCorrection = false
request.recognitionLanguages = ["zh-Hans", "en-US"]

let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
do {
    try handler.perform([request])
} catch {
    FileHandle.standardError.write("ocr failed: \(error)\n".data(using: .utf8)!)
    exit(4)
}

let width = cgImage.width
let height = cgImage.height
let observations = (request.results ?? []).sorted { a, b in
    if abs(a.boundingBox.midY - b.boundingBox.midY) > 0.02 { return a.boundingBox.midY > b.boundingBox.midY }
    return a.boundingBox.minX < b.boundingBox.minX
}

print("image: \(width)x\(height)")
for obs in observations {
    guard let candidate = obs.topCandidates(1).first else { continue }
    let box = obs.boundingBox
    let x = Int(box.minX * CGFloat(width))
    let y = Int((1 - box.maxY) * CGFloat(height))
    let w = Int(box.width * CGFloat(width))
    let h = Int(box.height * CGFloat(height))
    print("\(y),\(x),\(w),\(h)\t\(candidate.string)")
}
