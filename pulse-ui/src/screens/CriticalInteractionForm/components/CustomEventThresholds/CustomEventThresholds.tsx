import { Box, Grid, NumberInput, Text, TextInput } from "@mantine/core";
import classes from "./CustomEventThresholds.module.css";
import { CriticalInteractionFormData } from "../../CriticalInteractionForm.interface";
import { Controller, useFormContext } from "react-hook-form";
import { CRITICAL_INTERACTION_FORM_CONSTANTS } from "../../../../constants";

export function CustomEventThresholds() {
  const methods = useFormContext<CriticalInteractionFormData>();

  const onChangeLowThreshold = (event: React.ChangeEvent<HTMLInputElement>) => {
    if (!event?.target?.value) {
      methods.setError("uptimeLowerLimitInMs", {
        message: "Low threshold is required",
      });

      return;
    }

    checkForErrors(
      parseInt(event?.target?.value),
      methods.watch("uptimeMidLimitInMs"),
      methods.watch("uptimeUpperLimitInMs"),
    );
  };

  const onChangeMidThreshold = (event: React.ChangeEvent<HTMLInputElement>) => {
    if (!event?.target?.value) {
      methods.setError("uptimeMidLimitInMs", {
        message: "Mid threshold is required",
      });

      return;
    }

    checkForErrors(
      methods.watch("uptimeLowerLimitInMs"),
      parseInt(event?.target?.value),
      methods.watch("uptimeUpperLimitInMs"),
    );
  };

  const onChangeHighThreshold = (
    event: React.ChangeEvent<HTMLInputElement>,
  ) => {
    if (!event?.target?.value) {
      methods.setError("uptimeUpperLimitInMs", {
        message: "High threshold is required",
      });

      return;
    }

    checkForErrors(
      methods.watch("uptimeLowerLimitInMs"),
      methods.watch("uptimeMidLimitInMs"),
      parseInt(event?.target?.value),
    );
  };

  const checkForErrors = (low: number, mid: number, high: number) => {
    if (low > mid || low > high) {
      methods.setError("uptimeLowerLimitInMs", {
        message: "Low threshold should be less than mid threshold",
      });
    } else {
      methods.clearErrors("uptimeLowerLimitInMs");
    }
    if (mid < low || mid > high) {
      methods.setError("uptimeMidLimitInMs", {
        message: "Mid threshold should be between low and high threshold",
      });
    } else {
      methods.clearErrors("uptimeMidLimitInMs");
    }
    if (high < mid || high < low) {
      methods.setError("uptimeUpperLimitInMs", {
        message: "High threshold should be more than mid threshold",
      });
    } else {
      methods.clearErrors("uptimeUpperLimitInMs");
    }
  };

  return (
    <Box className={classes.CustomEventThresholdsMainContainer}>
      <Controller
        name="thresholdInMs"
        control={methods.control}
        rules={{
          required: CRITICAL_INTERACTION_FORM_CONSTANTS.ERROR_THRESHOLD_ERROR_MESSAGE,
          validate: (value) =>
            (Number.isInteger(Number(value)) && Number(value) > 0) ||
            CRITICAL_INTERACTION_FORM_CONSTANTS.ERROR_THRESHOLD_ERROR_MESSAGE,
        }}
        render={({ field, fieldState }) => (
          <Box className={classes.ErrorThresholdInputContainer}>
            <NumberInput
              {...field}
              label={CRITICAL_INTERACTION_FORM_CONSTANTS.ERROR_THRESHOLD_LABEL}
              description={CRITICAL_INTERACTION_FORM_CONSTANTS.ERROR_THRESHOLD_DESCRIPTION}
              placeholder={CRITICAL_INTERACTION_FORM_CONSTANTS.ERROR_THRESHOLD_PLACEHOLDER}
              error={fieldState.error?.message}
              min={1}
              allowDecimal={false}
              size="xs"
              onChange={(val) => field.onChange(val)}
            />
          </Box>
        )}
      />
      <Text>Interaction categorisation</Text>
      <Grid className={classes.CustomEventThresholdsGrid}>
        <Grid.Col span={4}>
          <TextInput
            {...methods.register("uptimeLowerLimitInMs", {
              required: true,
              min: CRITICAL_INTERACTION_FORM_CONSTANTS.LOWER_THRESHOLD_VALUE,
            })}
            size="xs"
            type="number"
            // placeholder={`${upTimeLowerLimit}`}
            label={"Low (ms)"}
            error={methods.formState.errors?.uptimeLowerLimitInMs?.message}
            onChange={onChangeLowThreshold}
            min={CRITICAL_INTERACTION_FORM_CONSTANTS.LOWER_THRESHOLD_VALUE}
          />
        </Grid.Col>
        <Grid.Col span={4}>
          <TextInput
            {...methods.register("uptimeMidLimitInMs", {
              required: true,
              min: CRITICAL_INTERACTION_FORM_CONSTANTS.MIDDLE_THRESHOLD_VALUE,
            })}
            size="xs"
            type="number"
            label={"Mid (ms)"}
            error={methods.formState.errors?.uptimeMidLimitInMs?.message}
            onChange={onChangeMidThreshold}
            min={CRITICAL_INTERACTION_FORM_CONSTANTS.MIDDLE_THRESHOLD_VALUE}
          />
        </Grid.Col>
        <Grid.Col span={4}>
          <TextInput
            {...methods.register("uptimeUpperLimitInMs", {
              required: true,
              min: CRITICAL_INTERACTION_FORM_CONSTANTS.UPPER_THRESHOLD_VALUE,
            })}
            size="xs"
            type="number"
            label={"High (ms)"}
            error={methods.formState.errors?.uptimeUpperLimitInMs?.message}
            onChange={onChangeHighThreshold}
            min={CRITICAL_INTERACTION_FORM_CONSTANTS.UPPER_THRESHOLD_VALUE}
          />
        </Grid.Col>
      </Grid>
    </Box>
  );
}
