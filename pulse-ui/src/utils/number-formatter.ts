export const hasValidPhoneNumber = (phone: string) =>
  phone.length === 10 && /^[0-9]+$/.test(phone);
export const hasValidNumber = (input: string) => /^[0-9]*$/.test(input);
