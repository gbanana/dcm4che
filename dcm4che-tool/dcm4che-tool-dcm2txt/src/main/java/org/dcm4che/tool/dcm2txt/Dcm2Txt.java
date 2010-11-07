package org.dcm4che.tool.dcm2txt;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.ElementDictionary;
import org.dcm4che.data.Fragments;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.dcm4che.data.VR;
import org.dcm4che.io.DicomInputHandler;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.util.TagUtils;

public class Dcm2Txt implements DicomInputHandler {

    private static final String USAGE = "dcm2txt [options] [infile]";

    private static final String DESCRIPTION = 
        "\nWrites a text representation of DICOM infile to standard output. " +
        "With no infile read standard input.\n.\nOptions:";

    private static final String EXAMPLE = null;

    /** default number of characters per line */
    private static final int DEFAULT_WIDTH = 78;

    private int width = DEFAULT_WIDTH;
    private boolean first = true;

    public final int getWidth() {
        return width;
    }

    public final void setWidth(int width) {
        if (width < 40)
            throw new IllegalArgumentException();
        this.width = width;
    }

    public void parse(InputStream is) throws IOException {
        DicomInputStream dis = new DicomInputStream(is);
        dis.setDicomInputHandler(this);
        dis.readAttributes(-1);
    }

    @Override
    public boolean readValue(DicomInputStream dis, Attributes attrs)
            throws IOException {
        if (first) {
            promptPreamble(dis.getPreamble());
            first = false;
        }
        StringBuilder line = new StringBuilder(width + 30);
        appendPrefix(dis, line);
        appendHeader(dis, line);
        VR vr = dis.vr();
        int vallen = dis.length();
        if (vr == null || vr == VR.SQ || vallen == -1) {
            appendKeyword(dis, line);
            System.out.println(line);
            return dis.readValue(dis, attrs);
        }
        int tag = dis.tag();
        dis.readValue(dis, attrs);
        line.append(" [");
        if (vr.toStringBuilder(attrs.getBytes(tag, null),
                dis.bigEndian(), attrs.getSpecificCharacterSet(),
                width - line.length() - 1, line)) {
            line.append(']');
            appendKeyword(dis, line);
        }
        System.out.println(line);
        switch (tag) {
        case Tag.FileMetaInformationGroupLength:
        case Tag.TransferSyntaxUID:
        case Tag.SpecificCharacterSet:
            break;
        default:
            if (!TagUtils.isPrivateCreator(tag))
                attrs.remove(tag, null);
        }
        return true;
    }

    @Override
    public boolean readValue(DicomInputStream dis, Sequence seq)
            throws IOException {
        StringBuilder line = new StringBuilder(width);
        appendPrefix(dis, line);
        appendHeader(dis, line);
        appendKeyword(dis, line);
        System.out.println(line);
        return dis.readValue(dis, seq);
    }

    @Override
    public boolean readValue(DicomInputStream dis, Fragments frags)
            throws IOException {
        StringBuilder line = new StringBuilder(width + 20);
        appendPrefix(dis, line);
        appendHeader(dis, line);
        boolean appendFragment = appendFragment(line, dis, frags.vr());
        System.out.println(line);
        return appendFragment || dis.readValue(dis, frags);
    }

    private void appendPrefix(DicomInputStream dis, StringBuilder line) {
        line.append(dis.getTagPosition()).append(": ");
        int level = dis.level();
        while (level-- > 0)
            line.append('>');
    }

    private void appendHeader(DicomInputStream dis, StringBuilder line) {
        line.append(TagUtils.toString(dis.tag())).append(' ');
        VR vr = dis.vr();
        if (vr != null)
            line.append(vr).append(' ');
        line.append('#').append(dis.length());
    }

    private void appendKeyword(DicomInputStream dis, StringBuilder line) {
        line.append(" ");
        line.append(ElementDictionary.keywordOf(dis.tag(), null));
        if (line.length() > width)
            line.setLength(width);
    }

    private boolean appendFragment(StringBuilder line, DicomInputStream dis,
            VR vr) throws IOException {
        if (dis.tag() != Tag.Item)
            return false;
        
        byte[] b = new byte[dis.length()];
        dis.readFully(b);
        line.append(" [");
        if (vr.toStringBuilder(b, dis.bigEndian(), null, 
                width - line.length() - 1, line)) {
            line.append(']');
            appendKeyword(dis, line);
        }
        return true;
    }

    private void promptPreamble(byte[] preamble) {
        if (preamble == null)
            return;
        
        StringBuilder line = new StringBuilder(width);
        line.append("0: [");
        if (VR.OB.toStringBuilder(preamble, false, null, width - 5, line))
            line.append(']');
        System.out.println(line);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        try {
            CommandLine cl = parseComandLine(args);
            if (cl != null) {
                Dcm2Txt dcm2txt = new Dcm2Txt();
                if (cl.hasOption("w")) {
                    String s = cl.getOptionValue("w");
                    try {
                        dcm2txt.setWidth(Integer.parseInt(s));
                    } catch (IllegalArgumentException e) {
                        throw new ParseException(
                                "Illegal line length: " + s);
                    }
                }
                InputStream is = inputStream(cl.getArgList());
                try {
                    dcm2txt.parse(is);
                } finally {
                    if (is != System.in)
                        is.close();
                }
            }
        } catch (ParseException e) {
            System.err.println("dcm2txt: " + e.getMessage());
            System.err.println("Try `dcm2txt --help' for more information.");
            System.exit(2);
        } catch (IOException e) {
            System.err.println("dcm2txt: " + e.getMessage());
            System.exit(2);
        }
    }

    private static InputStream inputStream(List<String> argList) 
            throws FileNotFoundException {
        return argList.isEmpty() ? System.in 
                                 : new FileInputStream(argList.get(0));
    }

    @SuppressWarnings("static-access")
    private static CommandLine parseComandLine(String[] args)
            throws ParseException{
        Options options = new Options();
        options.addOption(OptionBuilder
                .withLongOpt("width")
                .hasArg()
                .withArgName("col")
                .withDescription("set line length; default: 78")
                .create("w"));
        options.addOption(OptionBuilder
                .withLongOpt("help")
                .withDescription("display this help and exit")
                .create());
        options.addOption(OptionBuilder
                .withLongOpt("version")
                .withDescription("output version information and exit")
                .create());
        CommandLineParser parser = new PosixParser();
        CommandLine cl = parser.parse(options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(USAGE, DESCRIPTION, options, EXAMPLE);
            return null;
        }
        if (cl.hasOption("version")) {
            System.out.println("dcm2txt " + 
                    Dcm2Txt.class.getPackage().getImplementationVersion());
            return null;
        }
        return cl;
    }

}