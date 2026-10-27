package br.com.clicklance.api;

public class ApiException extends RuntimeException {
    public final int status;
    public final String code;
    public ApiException(int status, String code, String message) { super(message); this.status=status; this.code=code; }
    public static ApiException notFound() { return new ApiException(404,"NAO_ENCONTRADO","Recurso não encontrado."); }
    public static ApiException unauthorized() { return new ApiException(401,"NAO_AUTORIZADO","Credencial inválida ou recurso inativo."); }
    public static ApiException invalid(String message) { return new ApiException(400,"REQUISICAO_INVALIDA",message); }
    public static ApiException conflict(String message) { return new ApiException(409,"CONFLITO",message); }
}
