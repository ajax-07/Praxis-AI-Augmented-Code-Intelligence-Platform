package com.praxis.prism.internal;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads a .java file from disk and parses it into a CompilationUnit. Parsing is
 * lenient (BLEEDING_EDGE grammar) and failure-tolerant: an unparseable file is
 * skipped (empty Optional) rather than failing the whole analysis, because one
 * bad file in a large repo shouldn't sink the run.
 */
@Component
public class AstParser {

    private static final Logger log = LoggerFactory.getLogger(AstParser.class);

    private final JavaParser parser;

    public AstParser() {
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
        this.parser = new JavaParser(config);
    }

    public record ParsedFile(String relativePath, String source, CompilationUnit unit) {}

    public Optional<ParsedFile> parse(String relativePath, Path absolutePath) {
        try {
            String source = Files.readString(absolutePath);
            ParseResult<CompilationUnit> result = parser.parse(source);
            if (result.isSuccessful() && result.getResult().isPresent()) {
                return Optional.of(new ParsedFile(relativePath, source, result.getResult().get()));
            }
            log.debug("Skipping unparseable file {}", relativePath);
            return Optional.empty();
        } catch (IOException e) {
            log.warn("Could not read file {}", relativePath, e);
            return Optional.empty();
        }
    }
}
