package io.gdcc.spi.core.loader;

import java.util.List;

public class LoaderException extends RuntimeException {
    
    private final List<LoaderProblem> problems;
    
    public LoaderException(List<LoaderProblem> problems) {
        super("Multiple problems have been detected by the loader, accessible from getProblems().");
        this.problems = List.copyOf(problems);
    }
    
    public List<LoaderProblem> getProblems() {
        return problems;
    }
}
