import request from './request'

export const segmentationAPI = {
  segmentPolyp(formData) {
    return request.post('/polyp/segment', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  }
}