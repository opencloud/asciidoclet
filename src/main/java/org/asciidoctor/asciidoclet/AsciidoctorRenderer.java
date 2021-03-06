package org.asciidoctor.asciidoclet;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.sun.javadoc.Doc;
import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.Tag;
import org.asciidoctor.*;

import static org.asciidoctor.Asciidoctor.Factory.create;

/**
 * Doclet renderer using and configuring Asciidoctor.
 *
 * @author John Ericksen
 */
public class AsciidoctorRenderer implements DocletRenderer {

    private static AttributesBuilder defaultAttributes() {
        return AttributesBuilder.attributes()
                .attribute("at", "&#64;")
                .attribute("slash", "/")
                .attribute("icons", null)
                .attribute("idprefix", "")
                .attribute("javadoc", "")
                .attribute("notitle", null)
                .attribute("source-highlighter", "coderay")
                .attribute("coderay-css", "class");
    }

    private static OptionsBuilder defaultOptions() {
        return OptionsBuilder.options()
                .safe(SafeMode.SAFE)
                .backend("html5")
                .eruby("erubis");
    }

    protected static final String INLINE_DOCTYPE = "inline";

    private final Asciidoctor asciidoctor;
    private final Optional<OutputTemplates> templates;
    private final Options options;

    public AsciidoctorRenderer(DocletOptions docletOptions, DocErrorReporter errorReporter) {
        this(docletOptions, OutputTemplates.create(errorReporter), create());
    }

    /**
     * Constructor used directly for testing purposes only.
     */
    protected AsciidoctorRenderer(DocletOptions docletOptions, Optional<OutputTemplates> templates, Asciidoctor asciidoctor) {
        this.asciidoctor = asciidoctor;
        this.templates = templates;
        this.options = buildOptions(docletOptions, templates);
    }

    private Options buildOptions(DocletOptions docletOptions, Optional<OutputTemplates> templates) {
        OptionsBuilder opts = defaultOptions();
        if (docletOptions.includeBasedir().isPresent()) opts.baseDir(docletOptions.includeBasedir().get());
        if (templates.isPresent()) opts.templateDir(templates.get().templateDir());
        opts.attributes(buildAttributes(docletOptions));
        return opts.get();
    }

    private Attributes buildAttributes(DocletOptions docletOptions) {
        return defaultAttributes()
                .arguments(Iterables.toArray(docletOptions.attributes(), String.class))
                .get();
    }

    /**
     * Renders a generic document (class, field, method, etc)
     *
     * @param doc input
     */
    @Override
    public void renderDoc(Doc doc) {
        // hide text that looks like tags (such as annotations in source code) from Javadoc
        doc.setRawCommentText(doc.getRawCommentText().replaceAll("@([A-Z])", "{@literal @}$1"));

        StringBuilder buffer = new StringBuilder();
        buffer.append(render(doc.commentText(), false));
        buffer.append('\n');
        for ( Tag tag : doc.tags() ) {
            renderTag(tag, buffer);
            buffer.append('\n');
        }
        doc.setRawCommentText(buffer.toString());
    }

    public void cleanup() {
        if (templates.isPresent()) templates.get().delete();
    }

    /**
     * Renders a document tag in the standard way.
     *
     * @param tag input
     * @param buffer output buffer
     */
    private void renderTag(Tag tag, StringBuilder buffer) {
        //print out directly
        buffer.append(tag.name());
        buffer.append(" ");
        buffer.append(render(tag.text(), true));
    }

    /**
     * Renders the input using Asciidoctor.
     *
     * The source is first cleaned by stripping any trailing space after an
     * end line (e.g., `"\n "`), which gets left behind by the Javadoc
     * processor.
     *
     * @param input AsciiDoc source
     * @return content rendered by Asciidoctor
     */
    private String render(String input, boolean inline) {
        options.setDocType(inline ? INLINE_DOCTYPE : null);
        return asciidoctor.render(cleanJavadocInput(input), options);
    }

    protected static String cleanJavadocInput(String input){
        return input.trim()
            .replaceAll("\n ", "\n") // Newline space to accommodate javadoc newlines.
            .replaceAll("\\{at}", "&#64;") // {at} is translated into @.
            .replaceAll("\\{slash}", "/") // {slash} is translated into /.
            .replaceAll("(?m)^( *)\\*\\\\/$", "$1*/") // Multi-line comment end tag is translated into */.
            .replaceAll("\\{@literal (.*?)}", "$1"); // {@literal _} is translated into _ (standard javadoc).
    }
}
