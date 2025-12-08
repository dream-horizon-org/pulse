/**
 * Sampling Rate Slider Component
 * Visual slider for setting sampling rates (0-100%)
 */

import { Slider, Text, Box } from '@mantine/core';
import classes from '../../SamplingConfig.module.css';

interface SamplingRateSliderProps {
  label: string;
  value: number;
  onChange: (value: number) => void;
  disabled?: boolean;
}

export function SamplingRateSlider({ 
  label, 
  value, 
  onChange,
  disabled = false 
}: SamplingRateSliderProps) {
  const percentage = Math.round(value * 100);
  
  return (
    <Box className={classes.samplingRateContainer}>
      <Text className={classes.samplingRateLabel}>{label}</Text>
      <Slider
        className={classes.samplingRateSlider}
        value={percentage}
        onChange={(val) => onChange(val / 100)}
        min={0}
        max={100}
        step={1}
        disabled={disabled}
        marks={[
          { value: 0, label: '0%' },
          { value: 25, label: '25%' },
          { value: 50, label: '50%' },
          { value: 75, label: '75%' },
          { value: 100, label: '100%' },
        ]}
        styles={{
          track: {
            backgroundColor: 'rgba(14, 201, 194, 0.15)',
          },
          bar: {
            background: 'linear-gradient(90deg, #0ec9c2 0%, #0ba09a 100%)',
          },
          thumb: {
            borderColor: '#0ba09a',
            backgroundColor: '#ffffff',
          },
          mark: {
            backgroundColor: 'rgba(14, 201, 194, 0.3)',
          },
          markLabel: {
            fontSize: 10,
            color: 'var(--mantine-color-dark-4)',
          },
        }}
      />
      <Text className={classes.samplingRateValue}>{percentage}%</Text>
    </Box>
  );
}

