package com.eqdom.document.service;

public record FileDownload(byte[] data, String filename, String contentType) {
}
