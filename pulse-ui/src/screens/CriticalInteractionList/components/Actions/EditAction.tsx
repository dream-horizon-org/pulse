import { useNavigate } from "react-router-dom";
import {
  TOOLTIP_LABLES,
  ROUTES,
} from "../../../../constants";
import { Loader, Tooltip } from "@mantine/core";
import { IconEdit } from "@tabler/icons-react";
import { ActionProps } from "./Actions.interface";
import { useProjectContext } from "../../../../contexts";

export function EditAction({
  iconColor,
  name,
  isLoading,
}: ActionProps) {
  const navigate = useNavigate();
  const { projectId } = useProjectContext();

  const onClick = (event: React.MouseEvent) => {
    event.stopPropagation();
    
    if (!projectId) {
      console.error('[EditAction] Cannot navigate: projectId is null');
      return;
    }
    
    navigate(ROUTES.PROJECT_CRITICAL_INTERACTION_FORM.basePath
      .replace(':projectId', projectId)
      .replace('/*', `/${name}`));
  };

  if (isLoading) {
    return <Loader size={20} type="bars" />;
  }

  return (
    <Tooltip withArrow label={TOOLTIP_LABLES.EDIT_FORM}>
      <span
        onClick={onClick}
        style={{
          display: "flex",
          alignItems: "center",
          padding: "6px",
          background:
            "linear-gradient(135deg, rgba(14, 201, 194, 0.08), rgba(14, 201, 194, 0.12))",
          border: "1px solid rgba(14, 201, 194, 0.2)",
          borderRadius: "8px",
          transition: "all 0.3s cubic-bezier(0.4, 0, 0.2, 1)",
          boxShadow: "0 2px 6px rgba(14, 201, 194, 0.08)",
          cursor: "pointer",
        }}
        onMouseEnter={(e) => {
          (e.currentTarget as HTMLSpanElement).style.background =
            "linear-gradient(135deg, rgba(14, 201, 194, 0.12), rgba(14, 201, 194, 0.18))";
          (e.currentTarget as HTMLSpanElement).style.borderColor =
            "rgba(14, 201, 194, 0.3)";
          (e.currentTarget as HTMLSpanElement).style.transform =
            "translateY(-1px)";
          (e.currentTarget as HTMLSpanElement).style.boxShadow =
            "0 4px 12px rgba(14, 201, 194, 0.15)";
        }}
        onMouseLeave={(e) => {
          (e.currentTarget as HTMLSpanElement).style.background =
            "linear-gradient(135deg, rgba(14, 201, 194, 0.08), rgba(14, 201, 194, 0.12))";
          (e.currentTarget as HTMLSpanElement).style.borderColor =
            "rgba(14, 201, 194, 0.2)";
          (e.currentTarget as HTMLSpanElement).style.transform =
            "translateY(0)";
          (e.currentTarget as HTMLSpanElement).style.boxShadow =
            "0 2px 6px rgba(14, 201, 194, 0.08)";
        }}
      >
        <IconEdit color={iconColor} size={18} />
      </span>
    </Tooltip>
  );
}
