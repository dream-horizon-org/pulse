import {
  ActionIcon,
  Box,
  Button,
  MantineStyleProp,
  TextInput,
  useMantineTheme,
} from "@mantine/core";
import { IconX } from "@tabler/icons-react";
import { useEffect, useRef, useState } from "react";
import {
  hasValidNumber,
  hasValidPhoneNumber,
} from "../../utils/number-formatter";
import {
  MAX_PHONE_NUMBER_LENGTH,
  PHONE_NUMBER_TEXTS,
} from "./PhoneSearchBox.constants";
import classes from "./PhoneSearchBox.module.css";

type PhoneSearchBoxProps = {
  onSearch: (phoneNo: string) => void;
  onClear?: () => void;
  showClearIcon?: boolean;
  isDisabled: boolean;
  focusOnMount?: boolean;
  placeholder?: string;
  label?: string;
  ctaLabel?: string;
  buttonStyle?: MantineStyleProp;
  inputStyle?: MantineStyleProp;
  containerStyle?: MantineStyleProp;
};

export const PhoneSearchBox: React.FC<PhoneSearchBoxProps> = ({
  onSearch,
  onClear,
  showClearIcon,
  isDisabled,
  focusOnMount = false,
  placeholder = PHONE_NUMBER_TEXTS.PLACEHOLDER,
  label,
  ctaLabel = PHONE_NUMBER_TEXTS.CTA,
  buttonStyle,
  inputStyle,
  containerStyle,
}) => {
  const inputEleRef = useRef<HTMLInputElement>(null);
  const [phone, setPhone] = useState("");
  const shouldDisableButton = !hasValidPhoneNumber(phone) || isDisabled;
  const shouldShowClearIcon = phone.length > 0 && showClearIcon;
  const theme = useMantineTheme();

  const changeHandler = (event: React.ChangeEvent<HTMLInputElement>) => {
    const value = event.target.value;
    const isValidNumber = hasValidNumber(value);
    if (isValidNumber) {
      setPhone(value);
    }
  };

  const clearHandler = () => {
    setPhone("");
    onClear?.();
  };

  const ctaHandler = () => {
    onSearch(phone);
  };

  useEffect(() => {
    if (focusOnMount) {
      inputEleRef.current?.focus();
    }
  }, [focusOnMount]);

  return (
    <Box className={classes.container} style={{ ...containerStyle }}>
      <TextInput
        ref={inputEleRef}
        label={label ?? ""}
        placeholder={placeholder}
        value={phone}
        onChange={changeHandler}
        maxLength={MAX_PHONE_NUMBER_LENGTH}
        rightSection={
          !!shouldShowClearIcon && (
            <ActionIcon onClick={clearHandler} size="sm" variant="transparent">
              <IconX size={theme.fontSizes.lg} color={theme.colors.red[8]} />
            </ActionIcon>
          )
        }
        style={{ ...inputStyle }}
      />
      <Button
        onClick={ctaHandler}
        disabled={shouldDisableButton}
        style={buttonStyle}
      >
        {ctaLabel}
      </Button>
    </Box>
  );
};
