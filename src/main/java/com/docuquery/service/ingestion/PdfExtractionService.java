package com.docuquery.service.ingestion;

import com.docuquery.exception.ApiException;
import com.docuquery.exception.ErrorCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Extracts and cleans plain text from a PDF using Apache PDFBox 3.x (SPEC §6.1).
 */
@Service
public class PdfExtractionService {

    /**
     * @param pdfBytes raw bytes of an uploaded PDF
     * @return cleaned, whitespace-normalised text
     * @throws ApiException with {@link ErrorCode#PDF_EXTRACTION_FAILED} if extraction fails
     */
    public String extractText(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String raw = stripper.getText(document);
            return clean(raw);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.PDF_EXTRACTION_FAILED,
                    "Failed to extract text from PDF", e);
        }
    }

    /**
     * Replaces control characters (except tab, newline, carriage return) with
     * spaces, then collapses runs of whitespace into single spaces.
     */
    String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String noControl = raw.replaceAll("[\\p{Cntrl}&&[^\t\n\r]]", " ");
        return noControl.replaceAll("\\s+", " ").strip();
    }
}
