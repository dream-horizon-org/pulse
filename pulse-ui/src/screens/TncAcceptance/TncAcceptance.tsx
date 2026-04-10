import { Box, Button, Checkbox, Stack, Text } from "@mantine/core";
import {
  IconFileText,
  IconShieldCheck,
  IconLock,
  IconCheck,
  IconChevronRight,
  IconShieldLock,
} from "@tabler/icons-react";
import { useState } from "react";
import classes from "./TncAcceptance.module.css";
import { useAcceptTnc } from "../../hooks/useAcceptTnc";
import { TncStatusResponse } from "../../hooks/useGetTncStatus";
import { LoaderWithMessage } from "../../components/LoaderWithMessage";
import { showNotification } from "../../helpers/showNotification";

interface TncAcceptanceProps {
  tncStatus: TncStatusResponse;
  onAccepted?: () => void;
}

export function TncAcceptance({ tncStatus, onAccepted }: TncAcceptanceProps) {
  const [agreed, setAgreed] = useState(false);
  const [accepted, setAccepted] = useState(false);

  const { mutate: acceptTnc, isPending } = useAcceptTnc({
    onSettled: (data, error) => {
      if (data?.data && !data.error) {
        setAccepted(true);
        if (onAccepted) {
          setTimeout(() => onAccepted(), 1500);
        }
      } else {
        showNotification(
          "Error",
          data?.error?.message || "Failed to accept Terms & Conditions",
          <IconShieldCheck size={18} />,
          "#e74c3c",
        );
      }
    },
  });

  const handleAccept = () => {
    acceptTnc({ versionId: tncStatus.versionId });
  };

  if (accepted) {
    return (
      <Box className={classes.container}>
        <Box className={classes.card}>
          <Stack align="center" gap="md" className={classes.successContainer}>
            <div className={classes.successIcon}>
              <IconCheck size={32} color="#0ec9c2" stroke={2.5} />
            </div>
            <Text className={classes.successText}>
              Terms Accepted
            </Text>
            <Text className={classes.successSubtext}>
              Redirecting to your workspace...
            </Text>
            <LoaderWithMessage loadingMessage="" />
          </Stack>
        </Box>
        <div className={classes.bgDecoration1} />
        <div className={classes.bgDecoration2} />
        <div className={classes.bgDecoration3} />
      </Box>
    );
  }

  return (
    <Box className={classes.container}>
      <Box className={classes.card}>
        <Stack gap="xl">
          {/* Header */}
          <Stack gap="sm">
            <div className={classes.iconWrapper}>
              <IconShieldLock size={28} color="#0ba09a" stroke={1.8} />
            </div>
            <Text className={classes.title}>Terms & Conditions</Text>
            <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
              <span className={classes.versionBadge}>{tncStatus.version}</span>
              <Text className={classes.subtitle}>
                Please review and accept to continue
              </Text>
            </div>
          </Stack>

          {/* Document links */}
          <div className={classes.documentList}>
            {tncStatus.documents?.tos && (
              <a
                href={tncStatus.documents.tos}
                target="_blank"
                rel="noopener noreferrer"
                className={classes.documentLink}
              >
                <div className={`${classes.documentIcon} ${classes.documentIconTos}`}>
                  <IconFileText size={18} />
                </div>
                <div className={classes.documentMeta}>
                  <span className={classes.documentName}>Terms of Service</span>
                  <span className={classes.documentDesc}>Service usage terms and conditions</span>
                </div>
                <IconChevronRight size={16} className={classes.documentArrow} />
              </a>
            )}
            {tncStatus.documents?.aup && (
              <a
                href={tncStatus.documents.aup}
                target="_blank"
                rel="noopener noreferrer"
                className={classes.documentLink}
              >
                <div className={`${classes.documentIcon} ${classes.documentIconAup}`}>
                  <IconShieldCheck size={18} />
                </div>
                <div className={classes.documentMeta}>
                  <span className={classes.documentName}>Acceptable Use Policy</span>
                  <span className={classes.documentDesc}>Permitted use guidelines</span>
                </div>
                <IconChevronRight size={16} className={classes.documentArrow} />
              </a>
            )}
            {tncStatus.documents?.privacy_policy && (
              <a
                href={tncStatus.documents.privacy_policy}
                target="_blank"
                rel="noopener noreferrer"
                className={classes.documentLink}
              >
                <div className={`${classes.documentIcon} ${classes.documentIconPp}`}>
                  <IconLock size={18} />
                </div>
                <div className={classes.documentMeta}>
                  <span className={classes.documentName}>Privacy Policy</span>
                  <span className={classes.documentDesc}>How we handle your data</span>
                </div>
                <IconChevronRight size={16} className={classes.documentArrow} />
              </a>
            )}
          </div>

          {/* Consent checkbox */}
          <Checkbox
            checked={agreed}
            onChange={(event) => setAgreed(event.currentTarget.checked)}
            color="teal"
            radius="sm"
            label={
              <Text className={classes.consentText}>
                I have reviewed and agree to the{" "}
                <span className={classes.consentHighlight}>Terms of Service</span>,{" "}
                <span className={classes.consentHighlight}>Acceptable Use Policy</span>, and{" "}
                <span className={classes.consentHighlight}>Privacy Policy</span>.
              </Text>
            }
          />

          {/* Action button */}
          <Button
            disabled={!agreed || isPending}
            loading={isPending}
            onClick={handleAccept}
            fullWidth
            size="lg"
            radius="xl"
            style={{
              background: (!agreed || isPending)
                ? undefined
                : "linear-gradient(135deg, #0ec9c2 0%, #0ba09a 100%)",
              border: "none",
              fontWeight: 600,
              fontSize: "16px",
              height: 48,
              fontFamily: "'Roboto', sans-serif",
              letterSpacing: "-0.2px",
            }}
          >
            Accept & Continue
          </Button>
        </Stack>
      </Box>

      {/* Background decorations */}
      <div className={classes.bgDecoration1} />
      <div className={classes.bgDecoration2} />
      <div className={classes.bgDecoration3} />
    </Box>
  );
}
