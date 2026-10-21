package com.drtx.bank.payments.iso20022;

import java.io.StringWriter;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/** Minimal indenting StAX writer; values are escaped by StAX, so no injection through names or remittance text. */
final class XmlWriter {

    private final StringWriter out = new StringWriter();
    private final XMLStreamWriter xml;
    private int depth;

    XmlWriter(String namespace) {
        try {
            xml = XMLOutputFactory.newFactory().createXMLStreamWriter(out);
            xml.writeStartDocument("UTF-8", "1.0");
            newline();
            xml.writeStartElement("Document");
            xml.writeDefaultNamespace(namespace);
            depth++;
        } catch (XMLStreamException e) {
            throw new IllegalStateException(e);
        }
    }

    XmlWriter open(String name) {
        try {
            newline();
            xml.writeStartElement(name);
            depth++;
            return this;
        } catch (XMLStreamException e) {
            throw new IllegalStateException(e);
        }
    }

    XmlWriter close() {
        try {
            depth--;
            newline();
            xml.writeEndElement();
            return this;
        } catch (XMLStreamException e) {
            throw new IllegalStateException(e);
        }
    }

    XmlWriter leaf(String name, Object value) {
        return value == null ? this : leaf(name, value, null, null);
    }

    XmlWriter leaf(String name, Object value, String attr, String attrValue) {
        try {
            newline();
            xml.writeStartElement(name);
            if (attr != null) {
                xml.writeAttribute(attr, attrValue);
            }
            xml.writeCharacters(String.valueOf(value));
            xml.writeEndElement();
            return this;
        } catch (XMLStreamException e) {
            throw new IllegalStateException(e);
        }
    }

    /** {@code <Outer><FinInstnId><BICFI>bic</BICFI></FinInstnId></Outer>} */
    XmlWriter agent(String name, String bic) {
        return open(name).open("FinInstnId").leaf("BICFI", bic).close().close();
    }

    /** {@code <Outer><Id><IBAN>iban</IBAN></Id></Outer>} */
    XmlWriter account(String name, String iban) {
        return open(name).open("Id").leaf("IBAN", iban).close().close();
    }

    String finish() {
        try {
            depth--;
            newline();
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.close();
            return out.toString() + "\n";
        } catch (XMLStreamException e) {
            throw new IllegalStateException(e);
        }
    }

    private void newline() throws XMLStreamException {
        xml.writeCharacters("\n" + "  ".repeat(Math.max(depth, 0)));
    }
}
