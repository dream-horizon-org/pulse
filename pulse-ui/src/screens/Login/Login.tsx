import {
  Box,
  Text,
  useMantineTheme,
  Stack,
  Container,
  Image,
  Button,
} from "@mantine/core";
import { CredentialResponse, GoogleLogin } from "@react-oauth/google";
import classes from "./Login.module.css";
import {
  COMMON_CONSTANTS,
  COOKIES_KEY,
  LOGIN_PAGE_CONSTANTS,
  ROUTES,
} from "../../constants";
import { useNavigate } from "react-router-dom";
import { IconSquareRoundedX } from "@tabler/icons-react";
import { authenticateUser } from "../../helpers/authenticateUser";
import { useEffect, useState } from "react";
import { LoaderWithMessage } from "../../components/LoaderWithMessage";
import { setCookiesAfterAuthentication } from "../../helpers/setCookiesAfterAuthentication";
import { showNotification } from "../../helpers/showNotification";
import { getCookies } from "../../helpers/cookies";
import { checkRefreshTokenExpiration } from "../../helpers/checkRefreshTokenExpiration";
import { logEvent } from "../../helpers/googleAnalytics";
import {
  isGcpMultiTenantEnabled,
  signInWithGoogleGcp,
} from "../../helpers/gcpAuth";
import { lookupTenant, TenantLookupResponse } from "../../helpers/tenantLookup";

export function Login() {
  const navigate = useNavigate();
  const theme = useMantineTheme();
  const [isFetchingTokensFromServer, setIsFetchingTokensFromServer] =
    useState<boolean>(false);
  const [isLoadingTenant, setIsLoadingTenant] = useState<boolean>(false);
  const [tenantInfo, setTenantInfo] = useState<TenantLookupResponse | null>(null);
  const [tenantLookupError, setTenantLookupError] = useState<string | null>(null);

  const gcpMultiTenantEnabled = isGcpMultiTenantEnabled();

  const googleOAuthEnabled = process.env.REACT_APP_GOOGLE_OAUTH_ENABLED;
  const googleClientId = process.env.REACT_APP_GOOGLE_CLIENT_ID ?? "";
  const isDevMode = process.env.NODE_ENV === "development";

  // Determine if dummy login should be shown
  const shouldShowDummyLogin =
    !gcpMultiTenantEnabled &&
    (googleOAuthEnabled === "false" ||
      (isDevMode && (!googleClientId || googleClientId.trim() === "")));

  // Fetch tenant info on mount when GCP multi-tenant is enabled
  useEffect(() => {
    const fetchTenantInfo = async () => {
      if (!gcpMultiTenantEnabled) return;

      setIsLoadingTenant(true);
      setTenantLookupError(null);

      const { data, error } = await lookupTenant();

      if (data) {
        setTenantInfo(data);
      } else if (error) {
        setTenantLookupError(error.message || "Failed to lookup tenant");
      }

      setIsLoadingTenant(false);
    };

    fetchTenantInfo();
  }, [gcpMultiTenantEnabled]);

  useEffect(() => {
    const refreshToken = getCookies(COOKIES_KEY.REFRESH_TOKEN);
    if (refreshToken && refreshToken !== "undefined") {
      const isRefreshTokenExpired = checkRefreshTokenExpiration(refreshToken);
      if (!isRefreshTokenExpired) {
        navigate(ROUTES.HOME.basePath);
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const showErrorToast = (errorMessage: string) => {
    showNotification(
      COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
      errorMessage,
      <IconSquareRoundedX />,
      theme.colors.red[6],
    );
  };

  const onSuccess = async ({ credential }: CredentialResponse) => {
    setIsFetchingTokensFromServer(true);
    logEvent("User logged in", ROUTES.LOGIN.key);
    const { data, error } = await authenticateUser(credential);
    if (data) {
      await setCookiesAfterAuthentication(data);
      navigate(ROUTES.HOME.basePath);
      setIsFetchingTokensFromServer(false);
      return;
    }
    setIsFetchingTokensFromServer(false);
    if (error) {
      showErrorToast(error.message);
    }
  };

  const onError = () => {
    showErrorToast(COMMON_CONSTANTS.DEFAULT_ERROR_MESSAGE);
  };

  const onDummyLogin = async () => {
    setIsFetchingTokensFromServer(true);
    logEvent("User logged in (dummy)", ROUTES.LOGIN.key);
    // Use a dummy token that the backend will recognize
    const { data, error } = await authenticateUser("dev-id-token");
    if (data) {
      await setCookiesAfterAuthentication(data);
      navigate(ROUTES.HOME.basePath);
      setIsFetchingTokensFromServer(false);
      return;
    }
    setIsFetchingTokensFromServer(false);
    if (error) {
      showErrorToast(error.message);
    }
  };

  const onGcpSignIn = async () => {
    if (!tenantInfo?.gcpTenantId) {
      showErrorToast("Tenant information not available. Please refresh the page.");
      return;
    }

    const gcpTenantId = tenantInfo.gcpTenantId;
    setIsFetchingTokensFromServer(true);
    logEvent("User logged in (GCP)", ROUTES.LOGIN.key);

    try {
      const { idToken, email, tenantId: effectiveTenantId } =
        await signInWithGoogleGcp(gcpTenantId);
      const { data, error } = await authenticateUser(idToken, {
        tenantId: effectiveTenantId ?? gcpTenantId,
        userEmail: email,
      });
      if (data) {
        await setCookiesAfterAuthentication(data, {
          tenantId: effectiveTenantId ?? gcpTenantId,
          tenantName: tenantInfo.tenantName,
        });
        navigate(ROUTES.HOME.basePath);
        setIsFetchingTokensFromServer(false);
        return;
      }
      if (error) {
        showErrorToast(error.message);
      }
    } catch (err) {
      showErrorToast(
        err instanceof Error ? err.message : COMMON_CONSTANTS.DEFAULT_ERROR_MESSAGE,
      );
    }
    setIsFetchingTokensFromServer(false);
  };

  return (
    <Box className={classes.loginContainer}>
      <Container size="fluid" className={classes.mainWrapper}>
        <div className={classes.splitLayout}>
          {/* Left Side - Dashboard Preview */}
          <Box className={classes.leftSide}>
            <Box className={classes.dashboardPreview}>
              <Image
                src={(process.env.PUBLIC_URL || '') + "/landing_page.png"}
                alt="Dashboard Preview"
                className={classes.previewImage}
              />
            </Box>
          </Box>

          {/* Right Side - Hero & Login */}
          <Box className={classes.rightSide}>
            <Stack
              align="center"
              justify="center"
              className={classes.rightContent}
            >
              <Stack align="center" gap="md" className={classes.heroSection}>
                <h1 className={classes.mainHeading}>
                  All Your App Insights in One Dashboard
                </h1>
                <Text className={classes.subtitle}>
                  Real-time visibility into what matters most - your users'
                  experience
                </Text>
              </Stack>

              <Box className={classes.loginCard}>
                {isFetchingTokensFromServer || isLoadingTenant ? (
                  <LoaderWithMessage
                    className={classes.loader}
                    loadingMessage={
                      isLoadingTenant
                        ? "Loading tenant information..."
                        : LOGIN_PAGE_CONSTANTS.SIGNING_IN_MESSAGE
                    }
                  />
                ) : (
                  <Stack align="center" gap="lg">
                    <Text className={classes.loginPrompt}>
                      Sign in to get started
                    </Text>
                    {gcpMultiTenantEnabled ? (
                      tenantLookupError ? (
                        <Stack align="center" gap="md">
                          <Text c="red" size="sm">
                            {tenantLookupError}
                          </Text>
                          <Button
                            size="md"
                            radius="xl"
                            variant="outline"
                            onClick={() => window.location.reload()}
                          >
                            Retry
                          </Button>
                        </Stack>
                      ) : tenantInfo ? (
                        <Stack align="center" gap="md">
                          <Text size="sm" c="dimmed">
                            Signing in to: <strong>{tenantInfo.tenantName}</strong>
                          </Text>
                          <Button
                            size="lg"
                            radius="xl"
                            onClick={onGcpSignIn}
                            style={{
                              background:
                                "linear-gradient(135deg, #0ec9c2 0%, #0ba09a 100%)",
                              border: "none",
                              fontWeight: 600,
                              fontSize: "16px",
                              padding: "12px 32px",
                              minWidth: "240px",
                            }}
                          >
                            Sign in with Google
                          </Button>
                        </Stack>
                      ) : null
                    ) : shouldShowDummyLogin ? (
                      <Button
                        size="lg"
                        radius="xl"
                        onClick={onDummyLogin}
                        style={{
                          background:
                            "linear-gradient(135deg, #0ec9c2 0%, #0ba09a 100%)",
                          border: "none",
                          fontWeight: 600,
                          fontSize: "16px",
                          padding: "12px 32px",
                          minWidth: "240px",
                        }}
                      >
                        Sign in (Dev Mode)
                      </Button>
                    ) : (
                      <GoogleLogin
                        shape="pill"
                        size="large"
                        onSuccess={onSuccess}
                        onError={onError}
                      />
                    )}
                    <Text className={classes.loginSubtext}>
                      {gcpMultiTenantEnabled
                        ? "Sign in with your organization account"
                        : shouldShowDummyLogin
                          ? "Development mode: Using dummy authentication"
                          : "Access powerful analytics and insights"}
                    </Text>
                  </Stack>
                )}
              </Box>
            </Stack>
          </Box>
        </div>
      </Container>

      {/* Background decorative elements */}
      <div className={classes.bgDecoration1}></div>
      <div className={classes.bgDecoration2}></div>
      <div className={classes.bgDecoration3}></div>
    </Box>
  );
}
