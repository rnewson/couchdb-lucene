package com.github.rnewson.couchdb.lucene;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.SortField;

/**
 * A TypedField consists of a normal Lucene field name and a type.
 * 
 * @author robertnewson
 * 
 */
public final class TypedField {

    public static enum Type {
        DATE(SortField.LONG), DOUBLE(SortField.DOUBLE), FLOAT(SortField.FLOAT), INT(SortField.INT), LONG(SortField.LONG), STRING(
                SortField.STRING);

        private final int asSortField;

        private Type(final int asSortField) {
            this.asSortField = asSortField;
        }

        public int asSortField() {
            return asSortField;
        }

    }

    private static Pattern PATTERN = Pattern.compile("^(\\w+)(<(\\w+)>)?$");

    private final String name;

    private final Type type;

    public TypedField(final String string) throws ParseException {
        final Matcher matcher = PATTERN.matcher(string);

        if (!matcher.matches()) {
            throw new ParseException("Field '" + string + "' not recognized.");
        }

        this.name = matcher.group(1);
        try {
            this.type = matcher.group(3) == null ? Type.STRING : Type.valueOf(matcher.group(3).toUpperCase());
        } catch (final IllegalArgumentException e) {
            throw new ParseException("Unrecognized type '" + matcher.group(3) + "'");
        }
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    @Override
    public String toString() {
        return String.format("%s<%s>", name, type.toString().toLowerCase());
    }

}
