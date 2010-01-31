package com.github.rnewson.couchdb.lucene;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.queryParser.ParseException;

import com.github.rnewson.couchdb.lucene.couchdb.FieldType;

/**
 * A TypedField consists of a normal Lucene field name and a type.
 * 
 * @author robertnewson
 * 
 */
public final class TypedField {

    private static Pattern PATTERN = Pattern.compile("^(\\w+)(<(\\w+)>)?$");

    private final String name;

    private final FieldType type;

    public TypedField(final String string) throws ParseException {
        final Matcher matcher = PATTERN.matcher(string);

        if (!matcher.matches()) {
            throw new ParseException("Field '" + string + "' not recognized.");
        }

        this.name = matcher.group(1);
        try {
            this.type = matcher.group(3) == null ? FieldType.STRING : FieldType.valueOf(matcher.group(3).toUpperCase());
        } catch (final IllegalArgumentException e) {
            throw new ParseException("Unrecognized type '" + matcher.group(3) + "'");
        }
    }

    public String getName() {
        return name;
    }

    public FieldType getType() {
        return type;
    }

    @Override
    public String toString() {
        return String.format("%s<%s>", name, type.toString().toLowerCase());
    }

}
