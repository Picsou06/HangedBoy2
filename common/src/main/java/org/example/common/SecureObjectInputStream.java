package org.example.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.Set;

public class SecureObjectInputStream extends ObjectInputStream {

    private static final Set<String> ALLOWED_CLASSES = Set.of(
            "org.example.common.Message",
            "org.example.common.Message$Type",
            "java.lang.String",
            "java.lang.Enum",
            "java.lang.Number",
            "java.lang.Integer"
    );

    public SecureObjectInputStream(InputStream in) throws IOException {
        super(in);
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        if (!ALLOWED_CLASSES.contains(desc.getName()) && !desc.getName().startsWith("[")) {
            throw new InvalidClassException("Unauthorized deserialization attempt", desc.getName());
        }
        return super.resolveClass(desc);
    }
}
