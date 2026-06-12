import { useState } from "react";
import { IconHistory } from "@tabler/icons-react";
import { Button } from "@mantine/core";
import { QueryHistoryContent } from "./QueryHistoryContent";
import { DModal as Modal } from "../../../../components/Modal";

import { UNIVERSAL_QUERY_TEXTS } from "../../UniversalEventQuery.constants";

export const QueryHistory = () => {
  const [showModal, setShowModal] = useState<boolean>(false);

  const handleButtonClick = () => {
    setShowModal((prev) => !prev);
  };

  const renderContent = () => {
    return <QueryHistoryContent />;
  };

  const HISTORY_ICON = <IconHistory size={20} />;

  return (
    <>
      <Button
        leftSection={HISTORY_ICON}
        variant="outline"
        onClick={handleButtonClick}
      >
        {UNIVERSAL_QUERY_TEXTS.QUERY_HISTORY}
      </Button>
      {showModal && (
        <Modal
          title="Query History"
          size="60%"
          renderContent={renderContent}
          onClose={handleButtonClick}
          visible={showModal}
        />
      )}
    </>
  );
};
