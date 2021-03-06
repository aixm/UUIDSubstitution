# Description
The program is using an AIXM 5.1 BasicMessage comprising of BASELINEs as input to generate an output file, again an AIXM 5.1 BasicMessage, comprising of all the input BASELINEs with with a newly generated UUID in the identifier element, all references to the newly generated UUID will be updated accordingly, furthermore the sequence number will be set to `1` and the correction number will be set to `0`. In addition, all the input BASELINEs will be updated to the new effective date as validTime begin position and an optional annotation.


# Usage
```bash
aixm-uuid-subst [OPTIONS] <EFFECTIVE-DATE> <INPUT-FILE> <OUTPUT-FILE>
```
```
Options:
  -r, --remark TEXT       This text will be placed in the annotation element.
  --csv-output-file PATH  A CSV output file containing the old and the new
                          values of the identifiers.
  --pretty                The output file will be human readable, pretty
                          printed.
  -h, --help              Show this message and exit

Arguments:
  <EFFECTIVE-DATE>  The new effective date, e.g. "2022-12-24T00:00:00Z".
  <INPUT-FILE>      An AIXM 5.1 Basic Message file as input.
  <OUTPUT-FILE>     The output file.
```
Example:
```bash
aixm-uuid-subst --remark "UUID reset" "2022-12-24T00:00:00Z" input.xml output.xml
```

# System Requirements
Any operating system which is capable to run the Java Runtime Environment is supported.

At least JRE version 8 is required to execute this program.

Please note that it must either be the java command in the PATH variable or the environment variable JAVA_HOME must point to the JRE installation directory.