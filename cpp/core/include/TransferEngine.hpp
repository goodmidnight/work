#pragma once

#include <string>
#include <memory>
#include "SessionManager.hpp" // TransferCallback, FdRequestCallback 타입을 위해 필요

namespace transfer::core {

    class PeerNode; // 전방 선언 (헤더 의존성 최소화)

    /**
     * @class TransferEngine
     * @brief PeerNode과 SessionManager를 총괄하는 파사드(Facade) 엔진
     */
    class TransferEngine {
    public:
        TransferEngine();
        ~TransferEngine();

        // --- 외부(JNI) 콜백 주입 ---
        void set_transfer_callback(const TransferCallback &callback) const;
        void set_fd_request_callback(const FdRequestCallback &callback) const;
        void set_encryption_key(const std::string& key) const;

        // --- 엔진 제어 API ---
        bool startReceiver(uint16_t port) const;
        void startSender(const std::string& ip, uint16_t port, int session_count = 4) const;
        void send_file(const std::string& file_path) const;
        void stop() const;

    private:
        // 엔진이 내부적으로 관리하는 핵심 부서들
        std::unique_ptr<PeerNode> peer_node_;
        std::shared_ptr<SessionManager> session_manager_;
    };

} // namespace transfer::core