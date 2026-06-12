import { UseFormReturn } from "react-hook-form";
import { CriticalInteractionFormData } from "../../CriticalInteractionForm.interface";

export type JourneyNameProps = {
  isUpdateFlow: boolean;
  jobVersion: string;
  formMethods: UseFormReturn<CriticalInteractionFormData>;
  interactionName: string;

  interactionDescription: string;
  onNextClick: () => void;
  onChangeInActiveState: (isStateValid: boolean) => void;
};
