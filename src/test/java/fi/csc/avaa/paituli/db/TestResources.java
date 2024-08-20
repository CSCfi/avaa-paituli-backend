package fi.csc.avaa.paituli.db;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;

@WithTestResource(restrictToAnnotatedClass = false, value = H2DatabaseTestResource.class)
public class TestResources {
}
