package com.example.codecomplexity.service;

// Add these imports for Java Stream utilities
import java.util.stream.Stream;
import java.util.stream.Collectors;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.stream.Collectors;

/**
 * Core analysis service.
 * - Unzips uploaded file (or treats it as a single .java file)
 * - Parses Java files with JavaParser
 * - Computes:
 *    - Cyclomatic complexity (approx)
 *    - Maintainability index (approx using Halstead volume placeholder)
 *    - Duplication detection via method body hashing
 *
 * This implementation favors clarity and explainability over absolute academic correctness.
 */
@Service
public class ProjectAnalysisService {

    
    public static class FileMetrics {
        private String path;
        private int lines;
        private int cyclomatic;
        private double halsteadVolume;
        private double maintainabilityIndex;
        private java.util.List<String> duplicatedWith = new java.util.ArrayList<>();

        public FileMetrics() {}

        public FileMetrics(String path, int lines, int cyclomatic, double halsteadVolume, double maintainabilityIndex, java.util.List<String> duplicatedWith) {
            this.path = path;
            this.lines = lines;
            this.cyclomatic = cyclomatic;
            this.halsteadVolume = halsteadVolume;
            this.maintainabilityIndex = maintainabilityIndex;
            this.duplicatedWith = duplicatedWith == null ? new java.util.ArrayList<>() : duplicatedWith;
        }

        // getters and setters
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public int getLines() { return lines; }
        public void setLines(int lines) { this.lines = lines; }
        public int getCyclomatic() { return cyclomatic; }
        public void setCyclomatic(int cyclomatic) { this.cyclomatic = cyclomatic; }
        public double getHalsteadVolume() { return halsteadVolume; }
        public void setHalsteadVolume(double halsteadVolume) { this.halsteadVolume = halsteadVolume; }
        public double getMaintainabilityIndex() { return maintainabilityIndex; }
        public void setMaintainabilityIndex(double maintainabilityIndex) { this.maintainabilityIndex = maintainabilityIndex; }
        public java.util.List<String> getDuplicatedWith() { return duplicatedWith; }
        public void setDuplicatedWith(java.util.List<String> duplicatedWith) { this.duplicatedWith = duplicatedWith; }
    }

    public static class AnalysisResult {
        private java.util.List<FileMetrics> files = new java.util.ArrayList<>();
        private int totalFiles;
        private int totalLines;
        private double averageCyclomatic;
        private double averageMaintainability;

        public AnalysisResult() {}

        public AnalysisResult(java.util.List<FileMetrics> files, int totalFiles, int totalLines, double averageCyclomatic, double averageMaintainability) {
            this.files = files == null ? new java.util.ArrayList<>() : files;
            this.totalFiles = totalFiles;
            this.totalLines = totalLines;
            this.averageCyclomatic = averageCyclomatic;
            this.averageMaintainability = averageMaintainability;
        }

        // getters and setters
        public java.util.List<FileMetrics> getFiles() { return files; }
        public void setFiles(java.util.List<FileMetrics> files) { this.files = files; }
        public int getTotalFiles() { return totalFiles; }
        public void setTotalFiles(int totalFiles) { this.totalFiles = totalFiles; }
        public int getTotalLines() { return totalLines; }
        public void setTotalLines(int totalLines) { this.totalLines = totalLines; }
        public double getAverageCyclomatic() { return averageCyclomatic; }
        public void setAverageCyclomatic(double averageCyclomatic) { this.averageCyclomatic = averageCyclomatic; }
        public double getAverageMaintainability() { return averageMaintainability; }
        public void setAverageMaintainability(double averageMaintainability) { this.averageMaintainability = averageMaintainability; }
    }

    
    public AnalysisResult analyzeZipOrFile(MultipartFile multipart) throws Exception {
        Path tmp = Files.createTempDirectory("upload");
        boolean isZip = Optional.ofNullable(multipart.getOriginalFilename()).orElse("").toLowerCase().endsWith(".zip");
        if (isZip) {
            try (ZipInputStream zis = new ZipInputStream(multipart.getInputStream())) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    Path out = tmp.resolve(entry.getName());
                    Files.createDirectories(out.getParent());
                    try (OutputStream os = Files.newOutputStream(out)) {
                        zis.transferTo(os);
                    }
                }
            }
        } else {
            // treat as single file
            Path out = tmp.resolve(multipart.getOriginalFilename());
            Files.copy(multipart.getInputStream(), out, StandardCopyOption.REPLACE_EXISTING);
        }

        List<Path> javaFiles;
        try (Stream<Path> stream = Files.walk(tmp)) {
            javaFiles = stream.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }

        AnalysisResult result = new AnalysisResult();
        Map<String, FileMetrics> metricsMap = new HashMap<>();
        Map<String, String> methodHashes = new HashMap<>(); // hash -> path#method

        for (Path p : javaFiles) {
            try {
                String content = Files.readString(p);
                CompilationUnit cu = StaticJavaParser.parse(content);

                FileMetrics fm = new FileMetrics();
                fm.setPath(tmp.relativize(p).toString().replace("\\", "/"));
                fm.setLines(countLines(content));

                // Cyclomatic: sum over methods
                int cyclomaticSum = 0;
                double halsteadVolumeSum = 0.0;

                List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
                for (MethodDeclaration method : methods) {
                    int c = computeCyclomaticForMethod(method);
                    cyclomaticSum += c;

                    double v = computeSimpleHalstead(method.toString());
                    halsteadVolumeSum += v;

                    // duplication: normalize method body and hash
                    String normalized = method.toString().replaceAll("\s+", " ").trim();
                    String h = sha1(normalized);
                    if (methodHashes.containsKey(h)) {
                        String other = methodHashes.get(h);
                        // record duplication later
                        // append duplication link in both files
                        // we'll record after building all FileMetrics to ensure file entries exist
                        methodHashes.put(h, other + "||" + fm.getPath() + "#" + method.getNameAsString());
                    } else {
                        methodHashes.put(h, fm.getPath() + "#" + method.getNameAsString());
                    }
                }

                fm.setCyclomatic(Math.max(1, cyclomaticSum)); // at least 1
                fm.setHalsteadVolume(halsteadVolumeSum);
                fm.setMaintainabilityIndex(computeMaintainabilityIndex(fm.getHalsteadVolume(), fm.getCyclomatic(), fm.getLines()));

                metricsMap.put(fm.getPath(), fm);

            } catch (Exception ex) {
                // skip parse errors but continue
                System.err.println("Failed to parse " + p + " : " + ex.getMessage());
            }
        }

        // Process duplications
        for (Map.Entry<String, String> e : methodHashes.entrySet()) {
            String v = e.getValue();
            if (v.contains("||")) {
                String[] parts = v.split("\\|\\|");
                // each part is path#method, mark file-level duplication for all involved files
                Set<String> filesInvolved = new HashSet<>();
                for (String part : parts) {
                    String path = part.split("#")[0];
                    filesInvolved.add(path);
                }
                for (String f1 : filesInvolved) {
                    FileMetrics fm = metricsMap.get(f1);
                    if (fm != null) {
                        for (String f2 : filesInvolved) {
                            if (!f1.equals(f2) && !fm.getDuplicatedWith().contains(f2)) {
                                fm.getDuplicatedWith().add(f2);
                            }
                        }
                    }
                }
            }
        }

        List<FileMetrics> list = new ArrayList<>(metricsMap.values());
        int totalLines = list.stream().mapToInt(FileMetrics::getLines).sum();
        double avgC = list.stream().mapToInt(FileMetrics::getCyclomatic).average().orElse(0.0);
        double avgM = list.stream().mapToDouble(FileMetrics::getMaintainabilityIndex).average().orElse(0.0);

        result.setFiles(list);
        result.setTotalFiles(list.size());
        result.setTotalLines(totalLines);
        result.setAverageCyclomatic(avgC);
        result.setAverageMaintainability(avgM);

        // cleanup
        try { Files.walk(tmp).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete); } catch (Exception ignored){}

        return result;
    }

    private int countLines(String s) {
        return (int) s.lines().count();
    }

    private int computeCyclomaticForMethod(MethodDeclaration method) {
        // Start with 1 and add for decision points
        CyclomaticVisitor visitor = new CyclomaticVisitor();
        visitor.visit(method, null);
        return Math.max(1, visitor.getCount());
    }

    private double computeSimpleHalstead(String code) {
        // Very simple token-based approximation:
        String[] tokens = code.replaceAll("[^A-Za-z0-9_]", " ").split("\s+");
        List<String> toks = Arrays.stream(tokens).filter(t -> !t.isBlank()).collect(Collectors.toList());
        int N = toks.size();
        Set<String> unique = new HashSet<>(toks);
        int n = Math.max(1, unique.size());
        double V = N * (Math.log(n) / Math.log(2)); // volume
        if (Double.isNaN(V) || Double.isInfinite(V)) V = 0.0;
        return V;
    }

    private double computeMaintainabilityIndex(double vol, int cyclomatic, int loc) {
        // Classic-ish formula (scaled to 0-100)
        double mi = 171 - 5.2 * safeLn(vol) - 0.23 * cyclomatic - 16.2 * safeLn(Math.max(1, loc));
        double scaled = Math.max(0, (mi / 171.0) * 100.0);
        return Math.round(scaled * 100.0) / 100.0;
    }

    private double safeLn(double x) {
        if (x <= 0) return 0.0;
        return Math.log(x);
    }

    private String sha1(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] b = md.digest(s.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte by : b) sb.append(String.format("%02x", by));
        return sb.toString();
    }

    // Visitor to compute cyclomatic elements
    private static class CyclomaticVisitor extends com.github.javaparser.ast.visitor.VoidVisitorAdapter<Void> {
        private int count = 1;

        @Override
        public void visit(IfStmt n, Void arg) {
            count++;
            super.visit(n, arg);
        }

        @Override
        public void visit(ForStmt n, Void arg) {
            count++;
            super.visit(n, arg);
        }

        @Override
        public void visit(ForEachStmt n, Void arg) {
            count++;
            super.visit(n, arg);
        }

        @Override
        public void visit(WhileStmt n, Void arg) {
            count++;
            super.visit(n, arg);
        }

        @Override
        public void visit(DoStmt n, Void arg) {
            count++;
            super.visit(n, arg);
        }

        @Override
        public void visit(SwitchEntry n, Void arg) {
            // each 'case' increases complexity
            if (!n.getLabels().isEmpty()) count += n.getLabels().size();
            super.visit(n, arg);
        }

        @Override
        public void visit(CatchClause n, Void arg) {
            count++;
            super.visit(n, arg);
        }

        @Override
        public void visit(BinaryExpr n, Void arg) {
            // logical && and || increase complexity
            if (n.getOperator() == BinaryExpr.Operator.AND || n.getOperator() == BinaryExpr.Operator.OR) count++;
            super.visit(n, arg);
        }

        @Override
        public void visit(ConditionalExpr n, Void arg) {
            count++;
            super.visit(n, arg);
        }

        public int getCount() { return count; }
    }
}
