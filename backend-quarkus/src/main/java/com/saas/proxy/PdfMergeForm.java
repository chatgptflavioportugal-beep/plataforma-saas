package com.saas.proxy;

import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;

import java.io.File;

public class PdfMergeForm {

    @RestForm("file_a")
    @PartType("application/pdf")
    public File fileA;

    @RestForm("file_b")
    @PartType("application/pdf")
    public File fileB;
}
