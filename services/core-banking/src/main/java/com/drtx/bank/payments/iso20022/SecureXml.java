package com.drtx.bank.payments.iso20022;

import java.io.ByteArrayInputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

import com.drtx.bank.shared.error.DomainException;
import com.drtx.bank.shared.error.ErrorCode;

/** Hardened XML parsing (OWASP XXE cheat sheet): no DTDs, no external entities, no XInclude. */
public final class SecureXml {

    public static final int MAX_BYTES = 2 * 1024 * 1024;

    private SecureXml() {
    }

    public static Document parse(byte[] xml) {
        if (xml.length > MAX_BYTES) {
            throw new DomainException(ErrorCode.INVALID_MESSAGE, "Message exceeds " + MAX_BYTES + " bytes");
        }
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder builder = f.newDocumentBuilder();
            builder.setErrorHandler(null);
            return builder.parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new DomainException(ErrorCode.INVALID_MESSAGE, "Malformed or unsafe XML: " + e.getMessage());
        }
    }
}
