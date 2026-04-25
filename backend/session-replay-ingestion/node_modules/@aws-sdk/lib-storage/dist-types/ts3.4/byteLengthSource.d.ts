export declare enum BYTE_LENGTH_SOURCE {
  EMPTY_INPUT = "a null or undefined Body",
  CONTENT_LENGTH = "the ContentLength property of the params set by the caller",
  STRING_LENGTH = "the encoded byte length of the Body string",
  TYPED_ARRAY = "the byteLength of a typed byte array such as Uint8Array",
  LENGTH = "the value of Body.length",
  SIZE = "the value of Body.size",
  START_END_DIFF = "the numeric difference between Body.start and Body.end",
  LSTAT = "the size of the file given by Body.path on disk as reported by lstatSync",
}
export declare const byteLengthSource: (
  input: any,
  override?: number
) => BYTE_LENGTH_SOURCE | undefined;
