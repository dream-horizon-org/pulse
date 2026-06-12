import { ChangeEvent, useEffect, useState } from "react";
import classes from "./JourneyName.module.css";
import {
  Box,
  Button,
  TextInput,
  Tooltip,
  useMantineTheme,
  Text,
} from "@mantine/core";
import {
  CRITICAL_INTERACTION_FORM_CONSTANTS,
  PASCAL_CASE_FORM_REGEX,
} from "../../../../constants";
import { JourneyNameProps } from "./JourneyName.interface";
import { IconInfoCircle } from "@tabler/icons-react";

export function JourneyName({
  isUpdateFlow,
  interactionName,
  interactionDescription,
  formMethods,
  onNextClick,
  onChangeInActiveState,
}: JourneyNameProps) {
  const theme = useMantineTheme();
  const [name, setName] = useState<string>(interactionName);
  const [description, setDescription] = useState<string>(
    interactionDescription,
  );
  const [error, setError] = useState<boolean>(false);
  const [descError, setDescError] = useState<boolean>(false);

  useEffect(() => {
    onChangeInActiveState(testRegex());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [name]);

  const testRegex = () => {
    if (!name || error) {
      return false;
    }

    return PASCAL_CASE_FORM_REGEX.test(name);

    // switch (jobVersion) {
    //   case JOB_VERSIONS.V0: {
    //     return PASCAL_CASE_FORM_REGEX.test(name);
    //   }
    //   case JOB_VERSIONS.V1: {
    //     return FORM_REGEX.test(name);
    //   }
    //   default: {
    //     return FORM_REGEX.test(name);
    //   }
    // }
  };

  const onChange = (event: ChangeEvent<HTMLInputElement>) => {
    setName(event.target.value);
    formMethods.setValue("name", event.target.value);
    onChangeInActiveState(testRegex());
  };

  const onChangeDescription = (event: ChangeEvent<HTMLInputElement>) => {
    setDescription(event.target.value);
    formMethods.setValue("description", event.target.value);
    onChangeInActiveState(event.target.value.length > 0);
  };

  const onFocus = () => {
    if (error) {
      setError(false);
    }
  };

  const onDescFocus = () => {
    if (descError) {
      setDescError(false);
    }
  };

  const onNextButtonClick = () => {
    if (!testRegex()) {
      setError(true);
      return;
    }

    if (description.length === 0) {
      setDescError(true);
      return;
    }

    onNextClick();
  };

  return (
    <Box className={classes.eventNameContainer}>
      <TextInput
        disabled={isUpdateFlow}
        value={name}
        size={CRITICAL_INTERACTION_FORM_CONSTANTS.TEXT_INPUT_SIZE}
        radius={CRITICAL_INTERACTION_FORM_CONSTANTS.TEXT_INPUT_SIZE}
        label={
          <Box className={classes.eventNameLabel}>
            <Text>{CRITICAL_INTERACTION_FORM_CONSTANTS.INTERACTION_NAME}</Text>
            <Tooltip
              withArrow
              multiline
              // w={200}
              label={
                CRITICAL_INTERACTION_FORM_CONSTANTS.INTERACTION_DESCRIPTION
              }
            >
              <IconInfoCircle
                className={classes.labelIcon}
                color={theme.colors.primary[6]}
                size={25}
              />
            </Tooltip>
          </Box>
        }
        placeholder={CRITICAL_INTERACTION_FORM_CONSTANTS.INPUT_PLACEHOLDER}
        error={
          error && CRITICAL_INTERACTION_FORM_CONSTANTS.INTERACTION_ERROR_MESSAGE
        }
        onChange={onChange}
        onFocus={onFocus}
      />
      <TextInput
        value={description}
        size={CRITICAL_INTERACTION_FORM_CONSTANTS.TEXT_INPUT_SIZE}
        radius={CRITICAL_INTERACTION_FORM_CONSTANTS.TEXT_INPUT_SIZE}
        label={
          <Box className={classes.eventNameLabel}>
            <Text>
              {
                CRITICAL_INTERACTION_FORM_CONSTANTS.INTERACTION_DESCRIPTION_LABEL
              }
            </Text>
            <Tooltip
              withArrow
              multiline
              // w={200}
              label={
                CRITICAL_INTERACTION_FORM_CONSTANTS.INTERACTION_DESCRIPTION_TOOLTIP_LABEL
              }
            >
              <IconInfoCircle
                className={classes.labelIcon}
                color={theme.colors.primary[6]}
                size={25}
              />
            </Tooltip>
          </Box>
        }
        placeholder={CRITICAL_INTERACTION_FORM_CONSTANTS.INPUT_PLACEHOLDER}
        error={
          descError &&
          CRITICAL_INTERACTION_FORM_CONSTANTS.INTERACTION_DESCRIPTION_ERROR_MESSAGE
        }
        onChange={onChangeDescription}
        onFocus={onDescFocus}
      />
      <Box className={classes.nextButtonContainer}>
        <Button
          variant="filled"
          size="md"
          disabled={!name || error}
          onClick={onNextButtonClick}
        >
          {CRITICAL_INTERACTION_FORM_CONSTANTS.NEXT_BUTTON_TEXT}
        </Button>
      </Box>
    </Box>
  );
}
