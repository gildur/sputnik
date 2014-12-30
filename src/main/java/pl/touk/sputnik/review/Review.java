package pl.touk.sputnik.review;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.touk.sputnik.review.filter.FileFilter;
import pl.touk.sputnik.review.transformer.FileTransformer;

import java.util.*;

@Slf4j
@Getter
@Setter
public class Review {
    /* Source, severity, message, e.g. [Checkstyle] Info: This is bad */
    private static final String COMMENT_FORMAT = "[%s] %s: %s";
    private static final String PROBLEM_FORMAT = "There is a problem with %s: %s";

    private List<ReviewFile> files;
    private Map<Severity, Integer> violationCount = new EnumMap<>(Severity.class);
    private int totalViolationCount = 0;

    /**
     * Report problems with configuration, processors and other.
     * There problems should be displayed on review summary with your code-review tool
     *
     */
    private List<String> problems = new ArrayList<>();

    /**
     * Messages that will be displayed on review summary with your code-review tool
     */
    private List<String> messages = new ArrayList<>();
    private Map<String, Integer> scores = new HashMap<>();

    public Review(@NotNull List<ReviewFile> files) {
        this.files = files;
    }

    @NotNull
    public <T> List<T> getFiles(@NotNull FileFilter fileFilter, @NotNull FileTransformer<T> fileTransformer) {
        return fileTransformer.transform(fileFilter.filter(files));
    }

    @NotNull
    public List<String> getBuildDirs(@NotNull FileFilter fileFilter) {
        return Lists.transform(fileFilter.filter(files), new ReviewFileBuildDirFunction());
    }

    @NotNull
    public List<String> getSourceDirs(@NotNull FileFilter fileFilter) {
        return Lists.transform(fileFilter.filter(files), new ReviewFileSourceDirFunction());
    }

    public void addProblem(@NotNull String source, @NotNull String problem) {
        problems.add(String.format(PROBLEM_FORMAT, source, problem));
    }

    public void add(@NotNull String source, @NotNull ReviewResult reviewResult) {
        for (Violation violation : reviewResult.getViolations()) {
            addError(source, violation);
        }
    }

    public void addError(String source, Violation violation) {
        for (ReviewFile file : files) {
            if (file.getReviewFilename().equals(violation.getFilenameOrJavaClassName())
                    || file.getIoFile().getAbsolutePath().equals(violation.getFilenameOrJavaClassName())
                    || file.getJavaClassName().equals(violation.getFilenameOrJavaClassName())) {
                addError(file, source, violation.getLine(), violation.getMessage(), violation.getSeverity());
                return;
            }
        }
        log.warn("Filename or Java class {} was not found in current review", violation.getFilenameOrJavaClassName());
    }

    private void addError(@NotNull ReviewFile reviewFile, @NotNull String source, int line, @Nullable String message, Severity severity) {
        reviewFile.getComments().add(new Comment(line, String.format(COMMENT_FORMAT, source, severity, message)));
        incrementCounters(severity);
    }

    private void incrementCounters(Severity severity) {
        totalViolationCount += 1;
        Integer currentCount = violationCount.get(severity);
        violationCount.put(severity, currentCount == null ? 1 : currentCount + 1);
    }

    @NoArgsConstructor
    private static class ReviewFileBuildDirFunction implements Function<ReviewFile, String> {

        @Override
        public String apply(ReviewFile from) {
            return from.getBuildDir();
        }
    }

    @NoArgsConstructor
    private static class ReviewFileSourceDirFunction implements Function<ReviewFile, String> {

        @Override
        public String apply(ReviewFile from) {
            return from.getSourceDir();
        }
    }
}
